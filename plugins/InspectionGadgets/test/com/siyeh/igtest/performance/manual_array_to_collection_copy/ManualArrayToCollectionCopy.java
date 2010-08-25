package com.siyeh.igtest.performance.manual_array_to_collection_copy;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

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

    private static Random random = new Random();
    private static final int N = 10;
    public static int nextInt() {
        return random.nextInt(N);
    }

    public static void main(String[] args) {
        List<Integer>[] li = new List[N];
        for (int i = 0; i < N; ++i) {
            li[i] = new ArrayList<Integer>();
        }
        Integer[] values = new Integer[N];
        for (int i = 0; i < N; ++i) {
            values[i] = i;
        }
        //This loop is highlighted.
        for (int i = 1; i < N; ++i) {
            li[nextInt()].add(values[i]);
        }
    }
}
