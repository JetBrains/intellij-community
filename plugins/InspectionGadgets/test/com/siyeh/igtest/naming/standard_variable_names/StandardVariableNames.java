package com.siyeh.igtest.naming.standard_variable_names;

public class StandardVariableNames {

    void bad() {
        int c = 1;
        String ch = "";
        float d = 1;
        double f = 1;
        Object i, j, k, m, n;
        short l;
        char s;
        char str;
    }

    void goo() {
        char c, ch;
        float f;
        double d;
        Integer i, j, k, m, n;
        long l;
        String s, str;

        new java.io.OutputStream() {
            public void write(int b) throws IOException {}
        }
    }

}