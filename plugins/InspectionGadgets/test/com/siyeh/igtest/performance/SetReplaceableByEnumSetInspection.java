package com.siyeh.igtest.performance;

import com.siyeh.igtest.bugs.MyEnum;

import java.util.HashSet;

public class SetReplaceableByEnumSetInspection {
    public static void main(String[] args) {   
        final HashSet<MyEnum> myEnums = new HashSet<MyEnum>(); 
    }
}
