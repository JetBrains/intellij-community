package com.siyeh.igtest.confusing;

public class ConfusingOctalEscapeInspection {
    public static final String foo =  "asdf\01234";
    public static final String boo =  "asdf\01834";
}
