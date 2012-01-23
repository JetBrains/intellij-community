package com.siyeh.ipp.forloop.indexed;

class NormalForEachLoop {
    void foo(int[] is) {
        <caret>for (int i : is) {
            System.out.println(i);
        }
    }
}