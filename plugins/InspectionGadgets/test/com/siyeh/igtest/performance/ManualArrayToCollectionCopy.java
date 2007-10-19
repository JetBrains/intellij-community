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

    ArrayList<Integer> boom(int[] ints) {
        final ArrayList<Integer> list = new ArrayList<Integer>(ints.length);
        for (int i = 0; i < ints.length; ++i) {
            // no inspection/ quick fix here because it is an array of primitives
            list.add(ints[i]);
        }
        return list;
    }

    List<String> shouldWorkForForeachToo(String[] strings) {
        List<String> list = new ArrayList();
        for (String string : strings) {
            list.add(string);
        }
        return list;
    }

    void replaceWithSubList(List<String> parameters, String[] args) {
        for (int i = 2; i < args.length; ++i)
        {
            parameters.add(args[i]);
        }
    }
}
