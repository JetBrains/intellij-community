package com.siyeh.ipp.exceptions.mergeTry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

class OldStyle {
  void foo(File file1) {
    <caret>try {
      try {
        FileInputStream in = new FileInputStream(file1);
      } catch (FileNotFoundException e) {
        // log
      }
    } catch (Exception e) {
      // log
    }
  }
}