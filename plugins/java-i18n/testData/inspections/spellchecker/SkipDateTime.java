package testData.inspection;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

class Main {
  private static final String s1 = " <TYPO descr="Typo: In word 'CONASTANT'">CONASTANT</TYPO>";

  public static void main(String[] args) {
    System.out.println("1");
    new String("<TYPO descr="Typo: In word 'CONASTANT'">CONASTANT</TYPO>");
    Integer.valueOf("<TYPO descr="Typo: In word 'CONASTANT'">CONASTANT</TYPO>");
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(" CONASTANT");
    SimpleDateFormat simpleDateFormat2 = new SimpleDateFormat("   CONASTANT");

    simpleDateFormat.applyPattern( "CONASTANT");
    simpleDateFormat.applyLocalizedPattern("CONASTANT");

    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("CONASTANT");

    new DateTimeFormatterBuilder().appendPattern("CONASTANT");
  }
}
