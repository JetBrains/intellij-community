package com.siyeh.igtest.serialization;

import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.fixes.RenameFix;

import java.io.Serializable;

public class NonSerializableFieldInSerializableClass implements Serializable{
    private int foo;
    private int[] fooArray;
    private RenameFix fix;
    private RenameFix[] fixArray;
    private transient RenameFix fix2;
}
