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

}