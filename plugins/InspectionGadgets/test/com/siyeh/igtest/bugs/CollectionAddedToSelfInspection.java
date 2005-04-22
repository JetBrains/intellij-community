package com.siyeh.igtest.bugs;

import java.util.*;

public class CollectionAddedToSelfInspection {
    private List foo = new ArrayList();
    private Set bar = new HashSet();
    private Map baz = new HashMap();

    public void escher()
    {
        foo.add(foo);
        foo.set(0, foo);
        foo.add(bar);
        bar.add(bar);
        bar.add(foo);

        baz.put(baz, foo);
        baz.put(foo, baz);
        baz.put(foo, bar);
    }
}
