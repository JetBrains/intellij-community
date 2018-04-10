import org.jetbrains.annotations.NonNls;

class Test {
  @NonNls String s = getValue("<TYPO descr="Typo: In word 'CONASTANT'">CONASTANT</TYPO>");

  String getValue(String key) {
    return null;
  }
}