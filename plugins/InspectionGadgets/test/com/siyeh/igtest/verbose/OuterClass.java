package com.siyeh.igtest.verbose;

public class OuterClass
{
    public static enum UnnecessaryEnumModifier2Inspection {
        Red, Green, Blue;

        private UnnecessaryEnumModifier2Inspection() {
        }
    }
}