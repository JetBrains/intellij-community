package com.siyeh.igtest.abstraction.weaken_type;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class TypeMayBeWeakened {

    void weakness() {
        Map<Integer, String> map = new HashMap<Integer, String>();
        Integer key = 34;
        map.get(key);
    }

    public static String hashes(int len) {
        StringBuilder sb = new StringBuilder();
        for(int i=0;i<len;i++)
            sb.append('#');
        return sb.toString();
    }


    public static int exp(Iterable<String> list) {
        int i = 0;
        for (String s: list) {
            if ("some".equals(s)) {
                i++;
            }
        }
        return i;
    }

    class WeakBoolean {
        private Boolean myBool;

        WeakBoolean() {
            myBool = true;
        }

        public void inverse() {
            myBool = !myBool;
        }

        public void xor(boolean b) {
            myBool ^= b;
        }
    }

    void bar() {
        foo(new WeakBoolean());
    }

    void foo(WeakBoolean b) {
        System.out.println("b: " + b);
    }

    String foo(String s) {
        return s + 1;
    }

    private static void method() throws IllegalArgumentException {
        try {
            FileInputStream fis=new FileInputStream("/etc/modules");
        }
        catch(FileNotFoundException fnfex) {
            IllegalArgumentException iaex=new IllegalArgumentException("Exception Message");
            iaex.initCause(fnfex);
            throw iaex;
        }
    }

    public static void method(String weakened) {
        acceptsArray(new String[] {weakened});
    }

    public static void acceptsArray(String[] sarray) {}

    class Test {
        int x;
        void foo() { Test f = new Test(); f.x++; }
    }

    void listy(ArrayList list) {
        for (Object o : list) {

        }
    }
}