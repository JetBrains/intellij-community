package com.siyeh.igtest.style.unnecessary_valueof;

public class UnnecessaryCallToStringValueOf {

    String foo() {
        return "star" + String.valueOf(7);
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
      s = "abc" + String.valueOf('d') + "efg";
    }

    void printStream() {
        System.out.print(String.valueOf(7));
    }

    void builder(StringBuilder builder) {
        builder.append(String.valueOf(0x8));
    }

    public static void main22(String[] args) {
        foo(String.valueOf(3.0));
    }

    static void foo(String s) {
        System.out.println(s);
    }
}