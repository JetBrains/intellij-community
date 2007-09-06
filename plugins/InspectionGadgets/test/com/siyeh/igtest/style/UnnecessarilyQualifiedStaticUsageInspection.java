package com.siyeh.igtest.style;

import java.util.Vector;

public class UnnecessarilyQualifiedStaticUsageInspection {

    private static Object q;

    private static void r() {}
    class M {

        void r() {}

        void p() {
            int q;
            // class qualifier can't be removed
            UnnecessarilyQualifiedStaticUsageInspection.q = new Object();

            // can't be removed
            UnnecessarilyQualifiedStaticUsageInspection.r();

        }
    }

    void p() {
        // can be removed (not reported when "Only in static context" option is enabled)
        UnnecessarilyQualifiedStaticUsageInspection.q = new Object();

        // can be removed (not reported when "Only in static context" option is enabled)
        UnnecessarilyQualifiedStaticUsageInspection.r();
    }

    static void q() {
        // can be removed
        UnnecessarilyQualifiedStaticUsageInspection.q = new Object();

        final UnnecessarilyQualifiedStaticUsageInspection.M m;

        // can be removed
        UnnecessarilyQualifiedStaticUsageInspection.r();
    }
}

class TestUnnecessaryQualifiedNested
{
    static class Nested
    {
    }

    /**
     * A link to {@link TestUnnecessaryQualifiedNested.Nested} -- no warning here
     * <p/>
     * A link to {@link #doit(TestUnnecessaryQualifiedNested.Nested)} -- warns about an
     * unnecessary qualified static access but the quickfix does not work.
     *
     */
    public static void doit(Nested arg) {

    }
}