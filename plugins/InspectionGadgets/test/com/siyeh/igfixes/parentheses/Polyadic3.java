package com.siyeh.ipp.parentheses;

class Polyadic {
    boolean foo(int a, int b, int c, int d) {
        return a + (<caret>b - c) + d;
    }
}