package com.siyeh.igtest.style.unnecessary_super_qualifier;

public class UnnecessarySuperQualifier {

    private static class Base<T> {
        int field;

        void test(T v) {
        }
    }

    private static class Derived extends Base<String> {
        int field;

        void test(String v) {
            super.test(v);   // <-----
            System.out.println(super.field);
        }
    }

    private static class Up extends Base<String> {
        void foo() {
            super.test("asfd");
            System.out.println(super.field);
        }
    }
}