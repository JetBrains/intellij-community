package com.siyeh.igtest.performance;

import java.io.IOException;
import java.util.*;

public class CollectionsMustHaveInitialCapacityInspection
{
    public CollectionsMustHaveInitialCapacityInspection()
    {
    }

    public void foo() throws IOException
    {
      //  new HashMap<String, String>();
      //  new HashMap<String, String>(3);

        new HashMap();
        new HashMap(3);

        new WeakHashMap();
        new WeakHashMap(3);

        new HashSet();
        new HashSet(3);

        new Hashtable();
        new Hashtable(3);

        new BitSet();
        new BitSet(3);

        new Vector();
        new Vector(3);

        new ArrayList();
        new ArrayList(3);


    }
}