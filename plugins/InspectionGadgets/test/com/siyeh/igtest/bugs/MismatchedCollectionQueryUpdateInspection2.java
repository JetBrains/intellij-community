package com.siyeh.igtest.bugs;

import java.util.*;

public class MismatchedCollectionQueryUpdateInspection2 {
    private Set foo = new HashSet();

    public void foo()
    {
        final Set localFoo = foo;
    }

}
