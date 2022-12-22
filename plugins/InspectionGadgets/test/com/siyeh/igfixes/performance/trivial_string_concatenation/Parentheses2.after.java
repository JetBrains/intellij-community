package com.siyeh.igfixes.performance.trivial_string_concatenation;

class Parentheses2 {
    void m(String version) {
        /*hello*/
        final String s = " (" + "Groovy " + (version) + ")";
    }
}