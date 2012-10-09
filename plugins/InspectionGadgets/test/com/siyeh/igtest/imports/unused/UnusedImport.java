package com.siyeh.igtest.imports.unused;

import java.util.Map.*;
import static java.util.Map.*;
import static java.lang.Math.*;
import static java.lang.Integer.SIZE;
import java.util.List;
import java.util.ArrayList;

public class UnusedImport {

    //http://www.jetbrains.net/jira/browse/IDEADEV-29705
    static {
        System.out.println(""+PI);
    }


    // http://www.jetbrains.net/jira/browse/IDEADEV-25881
    private final List<Integer> list = new ArrayList<Integer>(SIZE);

    public void add(int i) {
        list.add(i);
        Entry entry;
    }
}