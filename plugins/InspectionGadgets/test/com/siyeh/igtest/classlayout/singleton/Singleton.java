package com.siyeh.igtest.classlayout.singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Singleton{
    private static Singleton ourInstance = new Singleton();

    public static Singleton getInstance(){
        return ourInstance;
    }

    private Singleton(){
    }
}
class NonSingleton {
    public static final NonSingleton EMPTY = new NonSingleton(Collections.<String>emptyList());

    private final List<String> values;

    private NonSingleton(List<String> values) {
        this.values = values;
    }

    public int size() {
        return values.size();
    }

    public String get(int index) {
        return values.get(index);
    }

    //this method makes the class a non-singleton
    public NonSingleton add(String s) {
        List<String> copy = new ArrayList<String>(values);
        copy.add(s);
        return new NonSingleton(copy);
    }
}