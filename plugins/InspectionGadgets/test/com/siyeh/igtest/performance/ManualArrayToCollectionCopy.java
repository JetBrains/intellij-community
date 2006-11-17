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

    List arg(Object[] os) {
        List list = new ArrayList();
        for (int i = 0; i < os.length - 1; i++) {
            list.add(os[i + 1]);
        }
        Object[] ps = new Object[os.length - 1];
        for (int i = 1; i < os.length - 1; i++) {
            ps[i - 1] = os[i];
        }
        return list;
    }
}
