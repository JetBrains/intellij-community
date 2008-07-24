package com.siyeh.igtest.style.field_final;

public class FieldMayBeFinal {

    private static String string;
    private static int i;

    static {
        string = null;
    }
    static {
        string = null;
    }

    private String other;
    {
        other = null;
    }
    private String ss;
    {
        ss = "";
    }
    {
        ss = "";
    }

    private int number;
    public FieldMayBeFinal() {
        number = 0;
    }

    public FieldMayBeFinal(int number) {
        this.number = number;
    }
}