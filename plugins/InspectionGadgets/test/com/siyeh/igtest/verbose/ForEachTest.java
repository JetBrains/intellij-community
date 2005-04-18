package com.siyeh.igtest.verbose;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

public class ForEachTest{
    public int baz(){                          
        int total = 0;
        final List ints = new ArrayList();
        for(Iterator iterator = ints.iterator(); iterator.hasNext();){
            final Integer value = (Integer) iterator.next();
            total += value.intValue();
        }
        return total;
    }

}
