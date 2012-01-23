package com.siyeh.ipp.forloop.indexed;

class NormalForEachLoop {
    void foo(int[] is) {
        for (int i1 = 0, isLength = is.length; i1 < isLength; i1++) {
            int i = is[i1];
            System.out.println(i);
        }
    }
}