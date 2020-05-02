package com.siyeh.igtest.performance.redundant_string_format_call;

import java.util.Locale;

import static java.lang.String.format;

public class RedundantStringFormatCall {

    public static final String A = String.format("%n");
    String b = <warning descr="Redundant call to 'String.format()'">String.format("no parameters")</warning>;
    String c = String.format("asdf%n" +
                             "asdf%n");
    String d = String.format("asdf" + "asdf" + "asdf%n");
    String e = <warning descr="Redundant call to 'String.format()'">format("test")</warning>;

    void m() {
        System.out.println(<warning descr="Redundant call to 'String.format()'">String.format("string contains %%n%n")</warning>); // ok
        System.out.println(<warning descr="Redundant call to 'String.format()'">String.format(Locale.ENGLISH, "string contains %%n%n")</warning>);
        System.out.<warning descr="Redundant call to 'printf()'">printf</warning>("empty battery");
    }
}