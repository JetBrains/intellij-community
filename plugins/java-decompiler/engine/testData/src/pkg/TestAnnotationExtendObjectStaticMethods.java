package org.example;

import org.jetbrains.annotations.Nullable;

public class TestAnnotationExtendObjectStaticMethods {
    public static void main(String[] args) {

    }
    public static native <T extends @Nullable Object> Iterable<T> concat(Iterable<? extends T> a, Iterable<? extends T> b);
    public static native <T extends @Nullable String> Iterable<T> concat2(Iterable<? extends T> a, Iterable<? extends T> b);
    public static native <T extends String> Iterable<T> concat3(Iterable<? extends T> a, Iterable<? extends T> b);
    public static native <T extends Object> Iterable<T> concat4(Iterable<? extends T> a, Iterable<? extends T> b);
}