import org.jetbrains.annotations.NonNls;

class Test {
  void m() {
    @NonNls String s = "CONASTANT"; // <TYPO descr="Typo: In word 'CONASTANT'">CONASTANT</TYPO> comment
  }
}