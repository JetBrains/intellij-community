package com.siyeh.igtest.performance.redundant_string_format_call;

import java.util.Locale;

import static java.lang.String.format;

public class RedundantStringFormatCall {

    public static final String A = String.format("%n");
    String b = String.<warning descr="Redundant call to 'format()'">format</warning>("no parameters");
    String c = String.format("asdf%n" +
                             "asdf%n");
    String d = String.format("asdf" + "asdf" + "asdf%n");
    String e = <warning descr="Redundant call to 'format()'">format</warning>("test");

    void m() {
        System.out.println(String.format("string contains %%n%n")); // ok
        System.out.println(String.format(Locale.ENGLISH, "string contains %%n%n"));
        System.out.<warning descr="Redundant call to 'printf()'">printf</warning>("empty battery");
    }
}