/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.igfixes.migration.try_finally_can_be_try_with_resources;

import java.io.*;

class MyAutoCloseable implements AutoCloseable {

  void foo() {
    System.out.println("foo");
  }

  @Override
  public void close() {
    System.out.println("close");
  }
}

class Java9 {

  public static void main(String[] args) throws FileNotFoundException {
    test(new MyAutoCloseable());
  }

  static void test(MyAutoCloseable m) throws FileNotFoundException {
      m.foo();
      try (m; MyAutoCloseable m1 = new MyAutoCloseable()) {
          m.foo();
      }
  }
}