package com.siyeh.igtest.style.unnecessary_tostring;

public class UnnecessaryToString {

    String foo(Object o) {
        return "star" + o.toString();
    }

    String bar() {
        char[] cs = {'!'};
        return "wars" + cs.toString();
    }

    void fizzz(Object o) {
        boolean c = true;
        System.out.println(o.toString() + c);
    }

    void polyadic(Object s) {
      s = "abc" + s.toString() + "efg";
    }

    void printStream(Object o) {
        System.out.print(o.toString());
    }

    void builder(StringBuilder builder, Object o) {
        builder.append(o.toString());
    }

  public static void main22(String[] args) {
    foo(args[0].toString());
  }

  static void foo(String s) {
    System.out.println(s);
  }
}