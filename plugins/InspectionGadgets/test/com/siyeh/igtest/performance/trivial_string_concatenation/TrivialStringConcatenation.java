package com.siyeh.igtest.performance.trivial_string_concatenation;

public class TrivialStringConcatenation {

    public void foo() {
        final String foo = "" + 4 + "" + 3;
        String bar = "" + new Integer(4) + "asdf";
        Float aFloat = new Float(3.0);
        String baz = "" + aFloat;

        String trivial = "" + " ";
    }
}
