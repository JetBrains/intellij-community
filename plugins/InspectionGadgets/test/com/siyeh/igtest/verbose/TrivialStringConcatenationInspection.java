package com.siyeh.igtest.verbose;

public class TrivialStringConcatenationInspection {

    public void foo() {
        final String foo = "" + 4 + "" + 3;
    }
}
