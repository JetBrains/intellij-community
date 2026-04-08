public class DeadVariableCode {
  public static void main(String[] args) {

  }
  public void test(int i) {
    A a = null;
    if (i == 1) {
      a = new A(1);
    } else {
      a = new A(i);
    }
    if (a.isEven()) {
      System.out.println("even2");
    } else {
      System.out.println("odd2");
    }
    this.doSomething(a);
  }

  public void doSomething(A a) {

  }

  record A(int b) {
    boolean isEven() {
      return true;
    }
  }
}
