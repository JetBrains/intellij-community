package com.siyeh.igtest.verbose;

public class TrivialStringConcatenationInspection {

    public void foo() {
        final String foo = "" + 4 + "" + 3;
        String bar = "" + new Integer(4);
        Float aFloat = new Float(3.0);
        String baz = "" + aFloat;

        String trivial = "" + " ";
    }
}
