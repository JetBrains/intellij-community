package com.siyeh.igtest.bugs;

import java.util.Locale;


public class MalformedFormatString {

    public void foo()
    {
        String.format("%", 3.0);
        System.out.printf("%", 3.0);
        System.out.printf("%q", 3.0);
        System.out.printf("%d", 3.0);
        System.out.printf(new Locale(""),"%d%s", 3.0, "foo");
    }
}
