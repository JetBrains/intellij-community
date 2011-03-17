package com.siyeh.igtest.resources.io.plain;

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
            e.printStackTrace();
        }
        str.close();
    }

    public void foo5() throws IOException {
        FileInputStream str;
        str = new FileInputStream("bar");
        try {
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
            e.printStackTrace();
        }
        str.close();
    }

    /*public void correct() throws IOException {
        FileInputStream str = null;
        InputStreamReader reader = null;
        try {
            str = new FileInputStream("xxxx");
            reader = new InputStreamReader(str);
        } finally {
            reader.close();
        }
    }*/

    public void correct2() throws IOException {
        FileInputStream str = new FileInputStream("xxxx");
        try {
            str.read();
        } finally {
            str.close();
        }
    }
    public void interrupting() throws IOException {
        FileInputStream str = new FileInputStream("xxxx");
        str.read();
        try {
            str.read();
        } finally {
            str.close();
        }
    }
    public FileInputStream escaped() throws IOException {
        return new FileInputStream("xxxx");
    }
    public FileInputStream escaped2() throws IOException {
        FileInputStream stream = new FileInputStream("xxxx");
        return stream;
    }

    public FileInputsStream automaticResouceManagement() throws IOException {
        try (FileInputStream in = new FileInputStream("in")) {
            in.read();
        }
    }
}
