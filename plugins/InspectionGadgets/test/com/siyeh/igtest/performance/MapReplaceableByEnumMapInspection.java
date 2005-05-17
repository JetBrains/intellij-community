package com.siyeh.igtest.performance;

import com.siyeh.igtest.bugs.MyEnum;

import java.util.HashSet;
import java.util.HashMap;

public class MapReplaceableByEnumMapInspection {
    public static void main(String[] args) {   
        final HashMap<MyEnum, Object> myEnums = new HashMap<MyEnum, Object>(); 
    }
}
