package com.siyeh.igtest.controlflow.for_loop_replaceable_by_while;

public class ForLoopReplaceableByWhile {

    void foo(int i) {
        for (; i < 10;) {
            i++;
        }
    }

    private static <T> SList<T> reverse(SList<T> r) {
        SList<T> res = null;
        for (; r != null; res = cons(r.car, res), r = r.cdr)
            System.out.println("");
        return res;
    }

    private static <T> SList<T> cons(T car, SList<T> res) {
        return null;
    }

    private class SList<T> {
        public T car;
        public SList<T> cdr;
    }
}
