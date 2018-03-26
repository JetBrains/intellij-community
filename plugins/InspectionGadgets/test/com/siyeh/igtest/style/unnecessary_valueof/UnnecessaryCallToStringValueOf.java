package com.siyeh.igtest.style.unnecessary_valueof;

public class UnnecessaryCallToStringValueOf {

    String foo() {
        return "star" + <warning descr="Unnecessary 'String.valueOf()' call">String.valueOf</warning>(7);
    }

    String bar() {
        char[] cs = {'!'};
        return "wars" + String.valueOf(cs);
    }

    void fizzz() {
        boolean c = true;
        int d = 'x';
        System.out.println(String.valueOf(d) + c);
    }

    void polyadic(String s) {
      s = "abc" + <warning descr="Unnecessary 'String.valueOf()' call">String.valueOf</warning>('d') + "efg";
    }

    void printStream() {
        System.out.print(<warning descr="Unnecessary 'String.valueOf()' call">String.valueOf</warning>(7));
    }

    void builder(StringBuilder builder) {
        builder.append(<warning descr="Unnecessary 'String.valueOf()' call">String.valueOf</warning>(0x8));
    }

    public static void main22(String[] args) {
        foo(String.valueOf(3.0));
    }

    static void foo(String s) {
        System.out.println(s);
    }

  void exception() {
    try {

    } catch (RuntimeException e) {
      org.slf4j.LoggerFactory.getLogger(UnnecessaryCallToStringValueOf.class).info("this: {}", String.valueOf(e));
    }
  }

  void smarter() {
    String f = <warning descr="Unnecessary 'String.valueOf()' call">String.valueOf</warning>("statistics") +
               ':' +
               <warning descr="Unnecessary 'String.valueOf()' call">String.valueOf</warning>(1);
  }

  void regression() {
    String s = "" + Integer.valueOf("asdf") + <warning descr="Unnecessary 'String.valueOf()' call">String.valueOf</warning>((nothing()));
    String t = "" + <warning descr="Unnecessary 'String.valueOf()' call">String.valueOf</warning>(something());
  }

  Object nothing() {
    return null;
  }

  @org.jetbrains.annotations.NotNull
  Object something() {
    return new Object();
  }

  void test(int i) {
    System.out.println("i = "+<warning descr="Unnecessary 'String.valueOf()' call">String.valueOf</warning>(i));
  }

  String forString(String s1, String s2) {
      if(s1 != null) {
        return <warning descr="Unnecessary 'String.valueOf()' call">String.valueOf</warning>(s1);
      }
      return String.valueOf(s2);
  }

  void toStringConversion() {
    boolean bool = System.nanoTime() % 2 == 0;

    String s1 = "bool: " + <warning descr="Unnecessary 'Boolean.toString()' call">Boolean.toString</warning>(bool);
    String s2 = "long: " + <warning descr="Unnecessary 'Long.toString()' call">Long.toString</warning>(System.nanoTime());
    String s3 = "float: " + <warning descr="Unnecessary 'Float.toString()' call">Float.toString</warning>(1.0f+2.0f+3.0f);
  }
}