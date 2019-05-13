package testData.inspection;

class SPITest3 {
  public void method() {
    String camelCase<TYPO descr="Typo: In word 'Ttest'">Ttest</TYPO> = "she is reading";
    String <TYPO descr="Typo: In word 'ttest'">ttest</TYPO> = "she is reading";
  }
}
