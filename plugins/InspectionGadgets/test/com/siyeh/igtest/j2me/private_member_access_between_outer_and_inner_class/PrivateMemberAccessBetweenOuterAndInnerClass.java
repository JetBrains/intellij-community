package com.siyeh.igtest.j2me.private_member_access_between_outer_and_inner_class;

public class PrivateMemberAccessBetweenOuterAndInnerClass {
    private String caption = "Button";
    private final int N = 100;
    private final Inner[] inners = new Inner[10];

    private void initialize() {
        System.out.println(caption);
        Object btn = new Object() {
            public void foo() {
                System.out.println(<warning descr="Access to private member of class 'PrivateMemberAccessBetweenOuterAndInnerClass' requires synthetic accessor">caption</warning>);
                System.out.println(N);
            }
        };
    }

    private static class Inner{}
}
class X {
    void test() {
        Private ref = new Private("access");
        System.out.println(ref.<warning descr="Access to private member of class 'Private' requires synthetic accessor">field</warning>);
        PrivateAccessor.printPrivate(ref);
        new PrivateAccessor().print(ref);
    }

    private static class Private {
        private final String field;
        Private(String value) {
            this.field = value;
        }
    }

    private static class PrivateAccessor {
        PrivateAccessor() {
            // prevent synthetic constructor
        }
        void print(Private ref) {
            System.out.println(ref.<warning descr="Access to private member of class 'Private' requires synthetic accessor">field</warning>);
        }
        static void printPrivate(Private ref) {
            System.out.println(ref.<warning descr="Access to private member of class 'Private' requires synthetic accessor">field</warning>);
        }
    }
}
class Y {
    void m() {
        String caption = new PrivateMemberAccessBetweenOuterAndInnerClass().<error descr="'caption' has private access in 'com.siyeh.igtest.j2me.private_member_access_between_outer_and_inner_class.PrivateMemberAccessBetweenOuterAndInnerClass'">caption</error>;
        //noinspection PrivateMemberAccessBetweenOuterAndInnerClass
        System.out.println(X.s);

        //noinspection SyntheticAccessorCall
        System.out.println(X.s);
    }

    static class X {
        private static String s = "";
    }
}