package com.siyeh.igtest.imports.unused;

public class Constants {

    public static final int SIZE = 213;

    private int field = 0; // I'm not an utility class.
    public static void instanceMatMethod() {}
    @SuppressWarnings("InnerClassMayBeStatic") public class InstanceInnerMaterial {}
}