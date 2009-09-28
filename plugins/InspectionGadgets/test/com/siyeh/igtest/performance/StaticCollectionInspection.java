package com.siyeh.igtest.performance;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class StaticCollectionInspection
{
    private static final Map s_map1 = new HashMap(10);
    private static final List s_map2 = new ArrayList(10);

    private StaticCollectionInspection()
    {
    }

}