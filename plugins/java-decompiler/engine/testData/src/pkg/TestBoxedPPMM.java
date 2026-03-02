package pkg;

public class TestBoxedPPMM {
  public static void main(String[] args) {
  }

  static void preIncrementSum() {
    Integer a = 3;
    System.out.println(++a + ++a);
  }

  static void preDecrementSum() {
    Integer a = 10;
    System.out.println(--a + --a);
  }

  static void standalone() {
    Integer a = 0;
    ++a;
    a++;
    --a;
    a--;
  }

  static void preIncrementInCall() {
    Integer a = 0;
    t(++a);
    t(++a);
  }

  private static void t(int x) {
  }
}