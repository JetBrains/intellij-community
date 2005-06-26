package com.siyeh.igtest.bugs;

import java.lang.Object;
import java.util.*;

public class MismatchedQueryUpdateInsepction3{
    public void foo()
    {
        final Map<String, String> anotherMap = new HashMap<String, String>();
        final SortedMap<String, String> map = new TreeMap<String, String>(anotherMap);
        final Iterator<String> it = map.keySet().iterator();
        while(it.hasNext()){
            Object o = it.next();

        }
    }

}
