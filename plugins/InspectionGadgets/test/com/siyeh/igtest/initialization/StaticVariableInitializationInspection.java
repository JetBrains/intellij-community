package com.siyeh.igtest.initialization;

public class StaticVariableInitializationInspection
{
    public static int s_fooBar;        // may not be initialized
    public static int s_fooBaz = 1;
    public static int s_fooBarangus;
    public static int s_fooBazongas;

    static
    {
        s_fooBarangus = 2;
        staticCall();
    }

    private static void staticCall()
    {
        s_fooBazongas = 3;
    }
}
