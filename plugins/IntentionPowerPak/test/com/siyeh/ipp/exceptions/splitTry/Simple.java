package com.siyeh.ipp.exceptions.splitTry;

import java.io.*;

public class Simple {
  void foo(File file1, File file2) throws IOException {
      tr<caret>y (FileInputStream in = new FileInputStream(file1); FileOutputStream out = new FileOutputStream(file2)) {

      }
  }
}