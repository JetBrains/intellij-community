public class AssResult {
  public void context(boolean b, String ps) {
    String vs = 'prefix'
    if (b) vs += ps  // no warning
    if (b) { vs += ps } // no warning
    <error descr="Cannot resolve symbol 'print'">print</error> <error descr="Variable 'vs' already defined">vs</error> = 4
    print(<warning descr="Usage of assignment expression result">vs = 4</warning>)
    println ps
  }

  def assUsed() {
    def a = 2
    <warning descr="Usage of assignment expression result">a = 3</warning>
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