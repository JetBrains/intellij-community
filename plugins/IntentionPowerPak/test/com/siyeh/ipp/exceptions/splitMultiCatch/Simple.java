package com.siyeh.ipp.exceptions.splitMultiCatch;

import java.io.*;

public class Simple {
  void foo() {
    try {
      Reader reader = new FileReader("");
    } <caret>catch (IndexOutOfBoundsException | FileNotFoundException e) {
    }
  }
}