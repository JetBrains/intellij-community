public class AssResult {
  public void context(boolean b, String ps) {
    String vs = 'prefix'
    if (b) vs += ps  // warning
    if (b) { vs += ps } // no warning
    print <warning descr="Result of assignment expression used">vs = 4</warning>
    println ps
  }

  def assUsed() {
    def a = 2
    <warning descr="Result of assignment expression used">a = 3</warning>
  }

  void assIsNotUsed() {
    def a = 2
    a = 3
  }

  final int value
  AssResult(int value) {
    this.value = value;
  }
}