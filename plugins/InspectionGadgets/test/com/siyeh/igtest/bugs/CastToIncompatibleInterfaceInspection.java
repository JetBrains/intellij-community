package com.siyeh.igtest.bugs;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class CastToIncompatibleInterfaceInspection {
    public void foo()
    {
        List list = (List) new HashMap();
        List list2 = (List) new ArrayList();
        if(new HashMap() instanceof List)
        {

        }
    }
}
