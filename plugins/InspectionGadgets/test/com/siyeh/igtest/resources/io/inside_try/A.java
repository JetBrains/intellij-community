package com.siyeh.igtest.resources.io.inside_try;

import java.io.*;

public class A {

    void foo() throws IOException {
        InputStream in = null;
        try {
            if (true) {
                in = new FileInputStream((File) null);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }
}
