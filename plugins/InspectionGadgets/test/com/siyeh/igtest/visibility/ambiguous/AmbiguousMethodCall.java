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
    class Y {
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
}
