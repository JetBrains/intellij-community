package com.siyeh.igtest.resources.io;

import java.io.*;

public class IOResourceInspection {
    public void foo() throws FileNotFoundException {
       new FileInputStream("bar");
    }

    public void foo2() throws FileNotFoundException {
        final FileInputStream str = new FileInputStream("bar");

    }

    public void foo25() throws FileNotFoundException {
        try {
            final FileInputStream str = new FileInputStream("bar");
        } finally {
        }

    }

    public void foo26() throws FileNotFoundException {
        try {
            final ByteArrayInputStream str = new ByteArrayInputStream(new byte[1024]);
        } finally {
        }

    }

    public void foo3() throws IOException {
        final FileInputStream str = new FileInputStream("bar");
        str.close();
    }

    public void foo4() throws IOException {
        FileInputStream str = null;
        try {
            str = new FileInputStream("bar");
        } catch (FileNotFoundException e) {
            e.printStackTrace(); //TODO
        }
        str.close();
    }

    public void foo5() throws IOException {
        FileInputStream str = null;
        try {
            str = new FileInputStream("bar");
        } finally {
            str.close();
        }
    }
    public void foo6() throws IOException {
        FileInputStream str = null;
        try {
            str = new FileInputStream("bar");
        } finally {
        }
    }

    public void foo7() throws IOException {
        FileInputStream str = null;
        BufferedInputStream str2 = null;
        try {
            str = new FileInputStream("bar");
            str2 = new BufferedInputStream(str);
        } catch (FileNotFoundException e) {
            e.printStackTrace(); //TODO
        }
        str.close();
    }

    public void correct() throws IOException {
        FileInputStream str = null;
        InputStreamReader reader = null;
        try {
            str = new FileInputStream("xxxx");
            reader = new InputStreamReader(str);
        } finally {
            reader.close();
        }
    }
}
