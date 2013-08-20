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

    public void automaticResouceManagement() throws IOException {
        try (FileInputStream in = new FileInputStream("in")) {
            in.read();
        }
    }

    void test1() throws IOException {
        final FileInputStream in = new FileInputStream("");
        in.close();
    }

    void test2() throws IOException {
        final FileInputStream in = new FileInputStream("");
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {}
        }
    }

    void test3() throws FileNotFoundException {
        final FileInputStream in = new FileInputStream("");
        try {

        } finally {
            silentClose(in);
        }
    }

    void silentClose(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {}
        }
    }

    void resourceAsStream() {
        String.class.getResourceAsStream("bla");
    }

  public static void c() throws IOException {
    InputStream in = new FileInputStream("");
    OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream("asd"));
    try {
      writer.write(0);
    } finally {
      in.close();
      writer.close();
    }
  }

  void escaper(InputStream in) {}

  void escaped3() throws FileNotFoundException {
    escaper(new FileInputStream(""));
  }

  void escaped4() throws FileNotFoundException {
    class X {
      X(InputStream s) {}
    }
    new X(new FileInputStream("")) {};
    InputStream s = new FileInputStream("");
    new X(s) {};
  }

  void escaped5() throws FileNotFoundException {
    InputStream in = new FileInputStream("");
    escaper(in);
  }
}
