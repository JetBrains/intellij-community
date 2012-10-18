package com.siyeh.igtest.style.unnecessary_enum_modifier;

public enum UnnecessaryEnumModifier {
    Red, Green, Blue;

    private UnnecessaryEnumModifier() {
    }

    static enum X {
      A, B, C
    }
}