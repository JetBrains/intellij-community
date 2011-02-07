public class AssResult {
  public void context(boolean b, String ps) {
    String vs = 'prefix'
    if (b) vs += ps  // warning
    if (b) { vs += ps } // no warning
    print <warning descr="Result of assignment expression used">vs = 4</warning>
    println ps
  }
}