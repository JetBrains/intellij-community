package com.siyeh.igtest.bugs;

import java.util.regex.Pattern;

public class MalformedRegularExpression {
    private static final String ASTERISK = "*";

    public void foo()
    {
        "bar".matches("*");
        "bar".matches(".*");
        Pattern.compile(ASTERISK);
        Pattern.compile(".*");
    }
}
