/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.igtest.resources.ioresource;

import java.io.*;

public class IOResource {
    public void foo() throws FileNotFoundException {
       new <warning descr="'FileInputStream' should be opened in front of a 'try' block and closed in the corresponding 'finally' block">FileInputStream</warning>("bar");
    }

    public void foo2() throws FileNotFoundException {
        final FileInputStream str = new <warning descr="'FileInputStream' should be opened in front of a 'try' block and closed in the corresponding 'finally' block">FileInputStream</warning>("bar");

    }

    public void foo25() throws FileNotFoundException {
        try {
            final FileInputStream str = new <warning descr="'FileInputStream' should be opened in front of a 'try' block and closed in the corresponding 'finally' block">FileInputStream</warning>("bar");
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
            str = new <warning descr="'FileInputStream' should be opened in front of a 'try' block and closed in the corresponding 'finally' block">FileInputStream</warning>("bar");
        } finally {
        }
    }
    public void foo7() throws IOException {
        FileInputStream str = null;
        InputStreamReader str2 = null;
        try {
            str = new FileInputStream("bar");
            str2 = new <warning descr="'InputStreamReader' should be opened in front of a 'try' block and closed in the corresponding 'finally' block">InputStreamReader</warning>(str);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        str.close();
    }
    public void correct() throws IOException {
        FileInputStream str = new FileInputStream("xxxx");
        InputStreamReader reader = new InputStreamReader(str);
        try {
        } finally {
            reader.close();
        }
    }

    public void correct2() throws IOException {
        FileInputStream str = new FileInputStream("xxxx");
        try {
            str.read();
        } finally {
            str.close();
        }
    }
    public void interrupting() throws IOException {
        FileInputStream str = new <warning descr="'FileInputStream' should be opened in front of a 'try' block and closed in the corresponding 'finally' block">FileInputStream</warning>("xxxx");
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
        String.class.<warning descr="'InputStream' should be opened in front of a 'try' block and closed in the corresponding 'finally' block">getResourceAsStream</warning>("bla");
    }

  public static void c() throws IOException {
    InputStream in = new <warning descr="'FileInputStream' should be opened in front of a 'try' block and closed in the corresponding 'finally' block">FileInputStream</warning>("");
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

  void insignificant() throws IOException {
      InputStream in = new FileInputStream("file");
      Object o;
      {;;};
      try {
          o = in.read();
      }
      finally {
          in.close();
      }
  }

  Reader escaped6(InputStream stream, String cs) {
    return cs == null ? new InputStreamReader(stream) : new InputStreamReader(stream, cs);
  }

  void closeable() {
    new <warning descr="'Closeable' should be opened in front of a 'try' block and closed in the corresponding 'finally' block">Closeable</warning>() {
      public void close() throws IOException {

      }
    };
  }
}
