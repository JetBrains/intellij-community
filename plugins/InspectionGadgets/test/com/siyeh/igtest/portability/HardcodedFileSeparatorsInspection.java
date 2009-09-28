package com.siyeh.igtest.portability;

public class HardcodedFileSeparatorsInspection
{
    public HardcodedFileSeparatorsInspection()
    {
    }

    public static void foo()
    {
        final String backSlash = "\\";
        final String slash           = "/";
        final String date           = "dd/MM/yy";
        final String date2           = "sdd/MM/yy";
        final String tag1           = "<foo/>";
        final String tag2           = "</foo>";
        final String url            = "jdbc:hsqldb:hsql://localhost:9013";
    }
}