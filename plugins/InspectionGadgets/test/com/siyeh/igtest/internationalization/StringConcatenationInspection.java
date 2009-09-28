package com.siyeh.igtest.internationalization;

import org.jetbrains.annotations.NonNls;

public class StringConcatenationInspection
{
    public StringConcatenationInspection()
    {
    }

    public void foo()
    {
        final String concat = "foo" + "bar";
        System.out.println("concat = " + concat);
    }

    public void boom() {
        @NonNls
        String string = "asdf" + " asdfasd";
        string += "asdfasd";
        boom("asdf" + "boom");
    }

    private void boom(@NonNls String s) {
    }
}