package com.siyeh.igtest.internationalization;

import java.util.Locale;

public class StringToUpperWithoutLocaleInspection {
    public void foo()
    {
        final String foo = "foo".toUpperCase();
        final String bar = "bar".toUpperCase(Locale.US);
    }
}
