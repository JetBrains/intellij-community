package testData.inspection;

class SPITest4 {
  public void method<TYPO descr="Typo: In word 'Ttest'">Ttest</TYPO>WithMistake() { }

  public void <TYPO descr="Typo: In word 'methad'">methad</TYPO>() { }
}
