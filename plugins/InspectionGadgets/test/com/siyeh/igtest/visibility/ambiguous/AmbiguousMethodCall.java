package com.siyeh.igtest.visibility.ambiguous;

public class AmbiguousMethodCall {

    class X {

        void m() {}

        class Inner extends Y {
            {
                <warning descr="Call to method 'm()' from superclass 'Y' looks like call to method from class 'X'">m</warning>(); // ambiguous
            }
        }
    }
    static class Xt {

        void m() {}

        static class Inner2 extends Y {{
            m(); // not ambiguous because from static context.
        }}
    }
    static class Y {
        void m() {}
    }

    class Z {
        void n() {}

        class Inner extends Object {
            {
                n(); // not ambiguous
            }

            void n() {}
        }
    }

    static class Ministry {
        public static void wasteMoney() {}
    }
    static class Government {

        public static void wasteMoney() {}
        static class MinistryOfSerendipity extends Ministry {{
            <warning descr="Call to method 'wasteMoney()' from superclass 'Ministry' looks like call to method from class 'Government'">wasteMoney</warning>();
        }}
    }
}
class Base {
    void method() {}
}

class Outer {
    int method(String s) {
        return s.length();
    }

    static class Inner extends Base {
        void caller() {
            method();
        }
    }
}
