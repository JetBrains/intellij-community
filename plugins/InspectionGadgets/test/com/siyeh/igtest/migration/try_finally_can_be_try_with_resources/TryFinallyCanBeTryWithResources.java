package com.siyeh.igtest.migration.try_finally_can_be_try_with_resources;

import java.io.FileOutputStream;
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
    try {
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
}