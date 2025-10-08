package org.example;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TestAnnotationExtendWildcard {
    public static void main(String[] args) {

    }

    List<? extends Object> getA() {
        return null;
    }

    List<?> getA5() {
        return null;
    }

    List<? extends String> getA3() {
        return null;
    }

    List<? extends @Nullable Object> getA2() {
        return null;
    }

    List<? extends @Nullable String> getA4() {
        return null;
    }
}