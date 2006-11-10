package com.siyeh.igtest.performance;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public class ManualArrayToCollectionCopy {

    void foo(Object[] xs, Object[] ys) {
        List list = new ArrayList();
        for (int i = 0; (i < (int)(xs.length)); i++) {
            list.add(xs[i]);
        }
        list.addAll(Arrays.asList(xs).subList(0, xs.length));
    }
}
