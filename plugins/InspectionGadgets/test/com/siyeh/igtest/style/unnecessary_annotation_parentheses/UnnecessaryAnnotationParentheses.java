package com.siyeh.igtest.style.unnecessary_annotation_parentheses;

import org.jetbrains.annotations.NotNull;

public class UnnecessaryAnnotationParentheses {

    @NotNull()
    Object foo() {
        return null;
    }

    @SuppressWarnings()
    String s;
}
