package com.siyeh.igtest.visibility.ambiguous;

public class AmbiguousMethodCall {

    class X {

        void m() {}

        class Inner extends Y {
            {
                m(); // ambiguous
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
