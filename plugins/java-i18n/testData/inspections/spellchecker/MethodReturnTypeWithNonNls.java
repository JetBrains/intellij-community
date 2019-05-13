import org.jetbrains.annotations.NonNls;

class Test {
  @NonNls
  public String foo() {
    System.out.println("<TYPO descr="Typo: In word 'CONASTANT'">CONASTANT</TYPO>");
    return null;
  }
}