package com.siyeh.igtest.performance.string_equals_empty_string;

public class StringEqualsEmptyString {

    void foo(String s) {
        boolean b = s.equals("");
        boolean c = "".equals(s);
        boolean d = "a".equals("b");
    }
}
