package com.siyeh.igtest.encapsulation.package_visible_inner_class;

public class PackageVisibleInnerClassInspection<T>
{
    class Barangus
    {

        public Barangus(int val)
        {
            this.val = val;
        }

        int val = -1;
    }

    Object foo() {
        return new Object() {};
    }

    enum E {
        ONE, TWO
    }

    interface I {
        void foo();
    }
}
enum Sample {
    Sample1() {
        @Override
        void test() {
        }
    },
    Sample2() {
        @Override
        void test() {
        }
    };

    abstract void test();
}