package com.siyeh.ipp.forloop.indexed;
import java.util.*;
class NormalForEachLoop implements List<Integer>{
    void foo() {
        for (int i1 = 0, thisSize = this.size(); i1 < thisSize; i1++) {
            Integer i = this.get(i1);
            System.out.println(i);
        }
    }
}