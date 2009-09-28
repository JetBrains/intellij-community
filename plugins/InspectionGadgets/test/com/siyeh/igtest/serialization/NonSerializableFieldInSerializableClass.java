package com.siyeh.igtest.serialization;

import com.siyeh.ig.fixes.RenameFix;

import java.io.Serializable;
import java.util.List;

public class NonSerializableFieldInSerializableClass implements Serializable{
    private int foo;
    private int[] fooArray;
    private RenameFix fix;
    private RenameFix[] fixArray;
    private transient RenameFix fix2;
    private List fix3;
    private List<RenameFix> fix5;
    private List<Integer> fix6;
}
