package testData.inspection;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

class Main {
  private static final String s = " CONASTANT";
  private static final String s1 = " <TYPO descr="Typo: In word 'CONASTANT'">CONASTANT</TYPO>";

  public static void main(String[] args) {
    System.out.println("1");


    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(s);
    String pattern = "   CONASTANT";
    SimpleDateFormat simpleDateFormat2 = new SimpleDateFormat(pattern);

    simpleDateFormat.applyPattern( "Errror");
    simpleDateFormat.applyLocalizedPattern("Errror");

    String error1 = "Errror";
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(error1);

    String error2 = "Errror";
    new DateTimeFormatterBuilder().appendPattern(error2);
  }
}
