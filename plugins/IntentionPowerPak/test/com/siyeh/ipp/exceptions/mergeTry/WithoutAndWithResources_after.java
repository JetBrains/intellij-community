package com.siyeh.ipp.exceptions.mergeTry;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

class WithoutAndWithResources {


  void foo(File file) {
      try (InputStreamReader reader =
                   new InputStreamReader(new FileInputStream(file), "utf-8")) {
          // do work
      } catch (IOException e) {
          throw new RuntimeException(e);
      }
  }
}