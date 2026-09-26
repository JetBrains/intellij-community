package pkg;

public class TestEnumInit {

  public static void main(String[] args) {

  }

  enum TestEnum {
    A, B, C;
  }

  enum TestEnum1 {

    A(0),
    B(1),
    C(2);

    private final int anInt;

    TestEnum1(int i) {
      anInt = i;
    }
  }

  enum TestEnum2 {

    A(0, "0"),
    B(1, "1"),
    C(2, "2");

    private final int anInt;

    TestEnum2(int i, String s) {
      anInt = i;
    }
  }
}