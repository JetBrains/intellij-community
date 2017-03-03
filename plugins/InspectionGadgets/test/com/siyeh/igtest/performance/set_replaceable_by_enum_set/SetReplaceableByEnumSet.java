package com.siyeh.igtest.performance;

import java.util.HashSet;
import java.util.Set;

public class SetReplaceableByEnumSet {
    public static void main(String[] args) {   
        final HashSet<MyEnum> myEnums = new <warning descr="'HashSet<MyEnum>' replaceable with 'EnumSet'">HashSet<MyEnum></warning>();
    }

    enum MyEnum{
        FOO, BAR, BAZ;
        Set<MyEnum> enums = new HashSet();
        // enum set here throws exception at runtime -> don't suggest it
    }
}
