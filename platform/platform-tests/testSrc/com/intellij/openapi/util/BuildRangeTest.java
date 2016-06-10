/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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