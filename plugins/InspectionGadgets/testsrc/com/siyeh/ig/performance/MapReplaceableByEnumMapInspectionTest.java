package com.siyeh.ig.performance;

import com.IGInspectionTestCase;

public class MapReplaceableByEnumMapInspectionTest extends IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/performance/map_replaceable_by_enum_map",
                new MapReplaceableByEnumMapInspection());
    }
}