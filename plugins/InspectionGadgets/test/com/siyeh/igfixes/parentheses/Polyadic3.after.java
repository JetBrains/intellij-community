package com.siyeh.ipp.parentheses;

class Polyadic {
    boolean foo(int a, int b, int c, int d) {
        return a + b - c<caret> + d;
    }
}