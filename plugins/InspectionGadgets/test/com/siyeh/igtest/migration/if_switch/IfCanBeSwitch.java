import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
class IfCanBeSwitch {
  void m1(int i) {  // ok
    <warning descr="'if' statement replaceable with 'switch' statement">if</warning> (i == 0) System.out.println("zero"); else if (i == 1) System.out.println("one"); else System.out.println("many");
  }

  void m1(char c) {  // ok
    <warning descr="'if' statement replaceable with 'switch' statement">if</warning> (c == '0') System.out.println("zero"); else if (c == '1') System.out.println("one"); else System.out.println("many");
  }

  void m1(byte i) {  // ok
    <warning descr="'if' statement replaceable with 'switch' statement">if</warning> (i == (byte)0) System.out.println("zero"); else if (i == (byte)1) System.out.println("one"); else System.out.println("many");
  }

  void m2(int i) {  // bad, long literals
    if (i == 0L) System.out.println("zero"); else if (i == 1L) System.out.println("one"); else System.out.println("many");
  }

  void m2(long l) {  // bad, long expression
    if (l == 0) System.out.println("zero"); else if (l == 1) System.out.println("one"); else System.out.println("many");
  }

  void polyadic() {
    String s = null;
    <warning descr="'if' statement replaceable with 'switch' statement">if</warning>  (s.equals("asdf") || s.equals("addd") || s.equals("lkjh")) {
      System.out.println("asdf");

    } else if (s.equals("null")) {
      System.out.println("null");

    } else {
      System.out.println("default");
    }
  }

  void nullable(@Nullable String s) {
    if ("a".equals((s))) {
      System.out.println(1);
    } else if ("b".equals(s)) {
      System.out.println(2);
    } else if ("c".equals(s)) {
      System.out.println(3);
    } else {
      System.out.println(4);
    }
  }

  void notNullSafe() {
    String narf = null;
    if ("foo".equals(narf)) {
      // do this
    } else if ("bar".equals(narf)){
      // do that
    }
    else {
      // do something else.
    }
  }

  void nullSafe(String earth) {
    <warning descr="'if' statement replaceable with 'switch' statement">if</warning> (earth.equals("foo")) {
    } else if ("bar".equals(earth)) {
    } else {
    }
  }

  void nullSafe2(@NotNull String narf) {
    <warning descr="'if' statement replaceable with 'switch' statement">if</warning> ("foo".equals((narf))) {
      // do this
    } else if ("bar".equals(narf)){
      // do that
    }
    else {
      // do something else.
    }
  }

  Num num;
  enum Num {
    ONE, TWO
  }
  Num getNum() {
    return num;
  }
  void ifWithGetterToSwitch() {
    if (getNum() == Num.ONE) {
      System.out.println(1);
    } else if (getNum() == Num.TWO) {
      System.out.println(2);
    } else {
      System.out.println("-");
    }
  }
}