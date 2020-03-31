// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

public class AssertionTest extends TestCase {
  public void testNoExtraLines() throws IOException {
    Assertion assertion = new Assertion(StringConvertion.TO_STRING);
    String lines = assertion.converToLines(new Object[]{"a", "b"});
    Assertion.compareLines(lines, new String[]{"a", "b"});
    try (BufferedReader reader = new BufferedReader(new StringReader(lines))) {
      assertEquals("a", reader.readLine());
      char[] buffer = new char[1];
      reader.read(buffer, 0, buffer.length);
      assertEquals("b", new String(buffer));
      assertEquals(-1, reader.read());
    }

    assertEquals("", assertion.converToLines(ArrayUtilRt.EMPTY_OBJECT_ARRAY));
  }

  public void testDefaultStringConvertion() {
    assertEquals("null", StringConvertion.DEFAULT.convert(null));
  }
}
