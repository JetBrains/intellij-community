package com.siyeh.igtest.bugs;

import java.util.*;

public class MismatchedCollectionQueryUpdateInspection2 {
    private Set foo = new HashSet();

    public void foo()
    {
        final Set localFoo = foo;
    }

    private static String foos() {
        final List bar = new ArrayList();
        bar.add("okay");
        return "not " + bar;
    }

}
