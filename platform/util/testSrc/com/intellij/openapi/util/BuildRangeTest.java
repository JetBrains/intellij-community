// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class BuildRangeTest {
  @Test
  public void composition() {
    assertNull(BuildRange.fromStrings(null, null));
    assertNull(BuildRange.fromStrings("", ""));
    assertNull(BuildRange.fromStrings("1", ""));
  }

  @Test
  public void validation() {
    assertNotNull(BuildRange.fromStrings("0", "100"));
    assertNotNull(BuildRange.fromStrings("123.456", "123.456"));

    try {
      BuildRange.fromStrings("654.321", "123.456");
      fail();
    }
    catch (IllegalArgumentException ignored) { }
  }

  @Test
  public void membership() {
    assertTrue(BuildRange.fromStrings("1", "1").inRange(BuildNumber.fromString("1")));
    assertTrue(BuildRange.fromStrings("1.2", "1.2").inRange(BuildNumber.fromString("1.2")));

    assertTrue(BuildRange.fromStrings("1", "1.2").inRange(BuildNumber.fromString("1.0")));
    assertTrue(BuildRange.fromStrings("1", "1.2").inRange(BuildNumber.fromString("1.2")));

    assertTrue(BuildRange.fromStrings("1", "1.*").inRange(BuildNumber.fromString("1")));
    assertTrue(BuildRange.fromStrings("1", "1.*").inRange(BuildNumber.fromString("1.999")));
    assertTrue(BuildRange.fromStrings("1", "1.*").inRange(BuildNumber.fromString("1.SNAPSHOT")));

    assertFalse(BuildRange.fromStrings("1", "1").inRange(BuildNumber.fromString("1.1")));
    assertFalse(BuildRange.fromStrings("1.2", "1.2").inRange(BuildNumber.fromString("1.1")));
    assertFalse(BuildRange.fromStrings("1.2", "1.2").inRange(BuildNumber.fromString("1.3")));
    assertFalse(BuildRange.fromStrings("1.2", "1.2").inRange(BuildNumber.fromString("2")));
    assertFalse(BuildRange.fromStrings("1", "1.*").inRange(BuildNumber.fromString("2")));
  }
}