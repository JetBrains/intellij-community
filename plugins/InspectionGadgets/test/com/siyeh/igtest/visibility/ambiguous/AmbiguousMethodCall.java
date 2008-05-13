package com.siyeh.igtest.visibility.ambiguous;

public class AmbiguousMethodCall {

    class X {

        void m() {}

        class Inner extends Y {
            {
                m();
            }
        }
    }
    class Y {
        void m() {}
    }
}
