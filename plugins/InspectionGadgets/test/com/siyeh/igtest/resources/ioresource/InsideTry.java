package com.siyeh.igtest.resources.ioresource;

import java.io.*;

public class InsideTry {

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

    public void correct() throws IOException {
        FileInputStream str = null;
        InputStreamReader reader = null;
        try {
            str = new FileInputStream("xxxx");
            reader = new InputStreamReader(str);
        }
        finally {
            reader.close();
        }
    }
}
