package com.siyeh.igtest.migration.try_finally_can_be_try_with_resources;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

class TryFinallyCanBeTryWithResources {

  public void read1() throws IOException {
    final InputStream stream = new InputStream() {
      @Override
      public int read() throws IOException {
        return 0;
      }
    };
    <warning descr="'try' can use automatic resource management">try</warning> {
      stream.read();
    } finally {
      stream.close();
    }
  }

  public void write2() throws IOException {
    final InputStream stream = new InputStream() {
      @Override
      public int read() throws IOException {
        return 0;
      }
    };
    try {
      stream.read();
    } finally {
      System.out.println(stream);
      stream.close();
    }
  }

  public void write3() throws IOException {
    InputStream in  = true ? new FileInputStream("null") : null;
    try {
      byte[] magicNumber = new byte[2];
      in.mark(2);
      in.read(magicNumber);
      in.reset();
      if (false) {
        in = new FileInputStream("in"); // var can't be (implicitly final) resource var, because it is reassigned here
      }
    } finally {
      in.close();
    }
  }

  public void read4() throws IOException {
    FileInputStream fileInputStream = null;
    FileInputStream bufferedInputStream = null;
    try {
      fileInputStream = new FileInputStream("s");
      bufferedInputStream = null; // don't report, one of the vars is reassigned
      bufferedInputStream = new FileInputStream("fileInputStream");
    } finally {
      bufferedInputStream.close();
      fileInputStream.close();
    }
  }
}