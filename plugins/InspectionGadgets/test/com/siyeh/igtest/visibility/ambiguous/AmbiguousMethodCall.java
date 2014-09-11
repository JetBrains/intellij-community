package com.siyeh.igtest.visibility.ambiguous;

public class AmbiguousMethodCall {

    class X {

        void m() {}

        class Inner extends Y {
            {
                <warning descr="Method 'm()' from superclass 'Y' called, when method from class 'X' might have been expected">m</warning>(); // ambiguous
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
