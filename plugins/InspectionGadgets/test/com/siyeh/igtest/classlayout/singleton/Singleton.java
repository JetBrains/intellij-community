package com.siyeh.igtest.classlayout.singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class <warning descr="Class 'Singleton' is a singleton">Singleton</warning>{
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
class <warning descr="Class 'Singleton2' is a singleton">Singleton2</warning> {

    private Singleton2() {}

    public static Singleton2 getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final Singleton2 INSTANCE = new Singleton2();
    }
}
enum No1 {
    ;
    void f() {}
}
enum No2 {
    ONE, TWO;

    void f() {}
}
enum No3 {
    INSTANCE
}
enum <warning descr="Enum 'Yes' is a singleton">Yes</warning> {
    INSTANCE;

    void f() {}
}