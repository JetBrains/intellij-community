package com.siyeh.igtest.performance.map_replaceable_by_enum_map;

import java.util.Collections; import java.util.HashMap;
import java.util.Map; import java.util.TreeMap;

public class MapReplaceableByEnumMap {

    public static void main(String[] args) {
        final HashMap<MyEnum, Object> myEnums = new HashMap<MyEnum, Object>();
    }

    enum MyEnum{
        foo, bar, baz;
        Map<MyEnum, Object> enums = new HashMap();
        // enum map here throws exception at runtime -> don't suggest it
    }

    void foo() {
        final Map<MyEnum, Object> map = new TreeMap(Collections.reverseOrder());
    }
}
