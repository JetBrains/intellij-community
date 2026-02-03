// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.testFramework.UsefulTestCase;

public class IndentDataTest extends UsefulTestCase {

  public void testCreateFromSpaces() {
    String sample = "         ";
    IndentData data = IndentData.createFrom(sample, 0, sample.length(), 4);
    assertEquals(9, data.getTotalSpaces());
    assertEquals(9, data.getIndentSpaces());
    assertEquals(0, data.getSpaces());
  }

  public void testCreateFromTabsAndSpaces() {
    String sample = "\t\t   ";
    IndentData data = IndentData.createFrom(sample, 0, sample.length(), 4);
    assertEquals(11, data.getTotalSpaces());
    assertEquals(8, data.getIndentSpaces());
    assertEquals(3, data.getSpaces());
  }

  public void testCreateFromTabsAndSpaces1() {
    String sample = "\t\t  \t  "; // -->|-->|ss>|ss
    IndentData data = IndentData.createFrom(sample, 0, sample.length(), 4);
    assertEquals(14, data.getTotalSpaces());
    assertEquals(8, data.getIndentSpaces());
    assertEquals(6, data.getSpaces()); // we assume spaces always start
                                       // the alignment even if there are tabs after
  }
}
