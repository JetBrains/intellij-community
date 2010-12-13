package com.siyeh.igtest.style.simplifiable_annotation;

public class SimplifiableAnnotation {

    @ SuppressWarnings(value = "blabla")
    @ Deprecated()
    Object foo() {
        return null;
    }
}