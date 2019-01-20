// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.testFramework.LightPlatformTestCase;

public class GotoLineNumberDialogTest extends LightPlatformTestCase {

  public void testGetCoordinates() {

    // Goto absolute line.
    assertGetCoordinates(17, 13, "0", 0, 0);
    assertGetCoordinates(17, 13, "40", 39, 0);
    assertGetCoordinates(17, 13, "1111111111111111", 17, 0);

    // Go to relative line.
    assertGetCoordinates(17, 13, "+10", 27, 0);
    assertGetCoordinates(17, 13, "-10", 7, 0);
    assertGetCoordinates(17, 13, "+10-14", 13, 0);

    // Go to mixed line.
    assertGetCoordinates(17, 13, "40+3", 42, 0);
    assertGetCoordinates(17, 13, "40+1+1+1+1+1+1", 45, 0);
    assertGetCoordinates(17, 13, "40-20", 19, 0);
    assertGetCoordinates(17, 13, "40-60", 0, 0);
    assertGetCoordinates(17, 13, "30+1111111111111111", 29, 0);

    // Go to absolute column.
    assertGetCoordinates(17, 13, ":40", 17, 39);
    assertGetCoordinates(17, 13, ":111111111111111", 17, 0);

    // Go to relative column.
    assertGetCoordinates(17, 13, ":-5", 17, 8);
    assertGetCoordinates(17, 13, ":+7", 17, 20);

    // Go to mixed column.
    assertGetCoordinates(17, 13, ":40+5-5+5-5", 17, 39);
    assertGetCoordinates(17, 13, ":40+1+2+3+4", 17, 49);
    assertGetCoordinates(17, 13, ":40+111111111111111", 17, 39);

    // Go to line + column.
    assertGetCoordinates(17, 13, "20:35", 19, 34);
    assertGetCoordinates(17, 13, "20:35:", 19, 34);
    assertGetCoordinates(17, 13, "+7:+3", 24, 16);
    assertGetCoordinates(17, 13, "40:+0", 39, 13);

    // Edge cases.
    assertGetCoordinates(17, 13, "40:", 39, 0);
    assertGetCoordinates(17, 13, "13 + 5 - 8 \t: 35 - 38 + 6", 9, 2);

    // Invalid input.
    assertGetCoordinatesInvalid("");
    assertGetCoordinatesInvalid("no number at all");
    assertGetCoordinatesInvalid("filename:50");
    assertGetCoordinatesInvalid("50+");
    assertGetCoordinatesInvalid(":50+");
  }

  /** Lines and columns are 0-based, human-generated input is 1-based. */
  private static void assertGetCoordinates(int line, int column, String input, int newLine, int newColumn) {
    GotoLineNumberDialog.Coordinates coord = GotoLineNumberDialog.getCoordinates(line, column, input);
    assertEquals(newLine + ":" + newColumn, coord.row + ":" + coord.column);
  }

  private static void assertGetCoordinatesInvalid(String input) {
    GotoLineNumberDialog.Coordinates coord = GotoLineNumberDialog.getCoordinates(-1, -1, input);
    assertNull(coord);
  }

}
