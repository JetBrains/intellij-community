package com.siyeh.igtest.style.unqualified_inner_class_access;

import java.util.Map.Entry;

public class Test<T> {
    private Class<Entry> entryClass;

    public Test() {
        Entry entry;
        entryClass = Entry.class;
    }

    public Test(int i) {
        final String test = Inner.TEST;
    }
    static class Inner {
        public static final String TEST = "test";
    }
}
class A {
  class X {}
}
class B extends A {
  void foo(X x) {}
}