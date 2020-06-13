package com.siyeh.ipp.forloop.indexed;

class NormalForEachLoop {
    void foo(int[] is) {
        for (int j = 0, isLength = is.length; j < isLength; j++) {
            int i = is[j];
            System.out.println(i);
        }
    }
}