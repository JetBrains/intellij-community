import org.jetbrains.annotations.Nullable;

class IfCanBeSwitch {
  void m1(int i) {  // ok
    if (i == 0) System.out.println("zero"); else if (i == 1) System.out.println("one"); else System.out.println("many");
  }

  void m1(char c) {  // ok
    if (c == '0') System.out.println("zero"); else if (c == '1') System.out.println("one"); else System.out.println("many");
  }

  void m1(byte i) {  // ok
    if (i == (byte)0) System.out.println("zero"); else if (i == (byte)1) System.out.println("one"); else System.out.println("many");
  }

  void m2(int i) {  // bad, long literals
    if (i == 0L) System.out.println("zero"); else if (i == 1L) System.out.println("one"); else System.out.println("many");
  }

  void m2(long l) {  // bad, long expression
    if (l == 0) System.out.println("zero"); else if (l == 1) System.out.println("one"); else System.out.println("many");
  }

  void polyadic() {
    String s = null;
    if  ("asdf".equals(s) || "addd".equals(s) || "lkjh".equals(s)) {
      System.out.println("asdf");

    } else if ("null".equals(s)) {
      System.out.println("null");

    } else {
      System.out.println("default");
    }
  }

  void nullable(@Nullable String s) {
    if ("a".equals(s)) {
      System.out.println(1);
    } else if ("b".equals(s)) {
      System.out.println(2);
    } else if ("c".equals(s)) {
      System.out.println(3);
    } else {
      System.out.println(4);
    }
  }
}