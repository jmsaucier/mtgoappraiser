package com.cd.mtgoappraiser;

import com.cd.mtgoappraiser.config.AppraiserConfig;
import com.cd.mtgoappraiser.csv.AppraisedCsvProducer;
import com.cd.mtgoappraiser.csv.MtgoCSVParser;
import com.cd.mtgoappraiser.http.mtgotraders.MtgoTradersHotListParser;
import com.cd.mtgoappraiser.model.AppraisedCard;
import com.cd.mtgoappraiser.model.Card;
import com.cd.mtgoappraiser.model.MarketCard;
import com.cd.mtgoappraiser.http.mtggoldfish.MtgGoldfishIndexRequestor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Cory on 7/16/2016.
 */
public class Main {
    public static void main(String [] args) {
        System.out.println("Starting mtgoappraiser...");
        //Setup spring context and load beans
        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(AppraiserConfig.class);

        MtgoCSVParser mtgoCsvParser = (MtgoCSVParser) applicationContext.getBean("mtgoCsvParser");
        MtgGoldfishIndexRequestor mtgGoldfishIndexRequestor = (MtgGoldfishIndexRequestor) applicationContext.getBean("mtgGoldfishIndexRequestor");
        AppraisedCsvProducer appraisedCsvProducer = (AppraisedCsvProducer) applicationContext.getBean("appraisedCsvProducer");
        MtgoTradersHotListParser mtgoTradersHotListParser = (MtgoTradersHotListParser) applicationContext.getBean("mtgoTradersHotListParser");
        URL mtgoCollectionUrl = (URL) applicationContext.getBean("mtgoCollectionUrl");
        //End spring setup

        List<Card> rawCards = mtgoCsvParser.getCards(mtgoCollectionUrl);

        List<String> indexUrls = mtgGoldfishIndexRequestor.getIndexUrls();

        //A little wonky. Simplest way I could figure out how to combine a list of lists from here - http://stackoverflow.com/questions/31706699/combine-stream-of-collections-into-one-collection-java-8
        List<MarketCard> mtgGoldfishRetailCards = indexUrls.stream()
                                                              .map(indexUrl -> mtgGoldfishIndexRequestor.getCardsFromPage(indexUrl))
                                                              .flatMap(Collection::stream)
                                                              .collect(Collectors.toList());

        //Remove duplicates since many of the indices from mtggoldfish will contain duplicates
        Set<MarketCard> mtgGoldfishRetailCardsSet = new HashSet<>(mtgGoldfishRetailCards);

        List<MarketCard> mtgoTradersBuylistCards = mtgoTradersHotListParser.getHotBuyListCards();

        //Create a list of appraised cards, basically involves cross referencing the cards from
        //the original CSV and slapping a quantity on them
        List<AppraisedCard> appraisedCards = mtgGoldfishRetailCardsSet.stream()
                .filter(marketCard -> rawCards.contains(marketCard))
                .map(marketCard -> {
            AppraisedCard appraisedCard = new AppraisedCard(marketCard);

            appraisedCard.setQuantity(rawCards.get(rawCards.indexOf(marketCard)).getQuantity());

            int indexOfHotBuyCard = mtgoTradersBuylistCards.indexOf(marketCard);

            if(indexOfHotBuyCard > -1) {
                appraisedCard.setMtgoTradersBuyPrice(mtgoTradersBuylistCards.get(indexOfHotBuyCard).getBuyPrice());
            }

            return appraisedCard;
        }).collect(Collectors.toCollection(ArrayList::new));

        appraisedCards.sort(new AppraisedCard.AppraisedCardComparator());

        appraisedCsvProducer.printAppraisedCards(appraisedCards);

        System.out.println("All done.");
        System.exit(0);
    }
}
