package com.siyeh.igtest.style.unnecessary_valueof;

public class UnnecessaryCallToStringValueOf {

    String foo() {
        return "star" + <warning descr="'String.valueOf(7)' can be simplified to '7'">String.valueOf(7)</warning>;
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
      s = "abc" + <warning descr="'String.valueOf('d')' can be simplified to ''d''">String.valueOf('d')</warning> + "efg";
    }

    void printStream() {
        System.out.print(<warning descr="'String.valueOf(7)' can be simplified to '7'">String.valueOf(7)</warning>);
    }

    void builder(StringBuilder builder) {
        builder.append(<warning descr="'String.valueOf(0x8)' can be simplified to '0x8'">String.valueOf(0x8)</warning>);
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
    String f = <warning descr="'String.valueOf(\"statistics\")' can be simplified to '\"statistics\"'">String.valueOf("statistics")</warning> +
               ':' +
               <warning descr="'String.valueOf(1)' can be simplified to '1'">String.valueOf(1)</warning>;
  }

  void regression() {
    String s = "" + Integer.valueOf("asdf") + String.valueOf((nothing()));
    String t = "" + <warning descr="'String.valueOf(something())' can be simplified to 'something()'">String.valueOf(something())</warning>;
  }

  Object nothing() {
    return null;
  }

  @org.jetbrains.annotations.NotNull
  Object something() {
    return new Object();
  }
}