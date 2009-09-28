package com.siyeh.igtest.controlflow;

import java.io.InputStream;
import java.io.IOException;
import java.util.Iterator;

public class LoopConditionNotUpdatedInsideLoopInspection {

    void foo(InputStream in) throws IOException {
        final int i = in.read();
        while (i != -1) {

        }
    }

    Object next() {
        return null;
    }

    void arg() {
        Object o = next();
        while (o != null) {

        }
    }

    void boom() {
        int count = 0;
        while (count < 10) {
            System.out.println("count = " + count);
        }
    }

    void iterate(Iterator iterator) {
        while (iterator.hasNext()) {
            
        }
    }
}