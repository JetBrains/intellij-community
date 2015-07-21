/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.text;

import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

public class SemVerTest extends TestCase {
  public void testParsing() throws Exception {
    checkParsed("0.9.2", 0, 9, 2);
  }

  public void testExtendedVersion() throws Exception {
    checkParsed("0.9.2-dart", 0, 9, 2);
  }

  public void testGulp4Alpha() throws Exception {
    checkParsed("4.0.0-alpha.1", 4, 0, 0);
  }

  public void testMisc() throws Exception {
    checkParsed("0.10.0-rc-1", 0, 10, 0);
    checkParsed("1.0.0-rc-1", 1, 0, 0);
    checkParsed("1.0.0-alpha", 1, 0, 0);
    checkParsed("1.0.0-0.3.7", 1, 0, 0);
    checkParsed("1.0.0-x.7.z.92", 1, 0, 0);
    checkNotParsed("1.0.a");
    checkNotParsed("1.0");
    checkNotParsed("1..a");
  }

  private static void checkParsed(@NotNull String version, int expectedMajor, int expectedMinor, int expectedPatch) {
    assertEquals(new SemVer(version, expectedMajor, expectedMinor, expectedPatch), parseNotNull(version));
  }

  private static void checkNotParsed(@NotNull String version) {
    assertNull(SemVer.parseFromText(version));
  }

  public void testCompare() throws Exception {
    assertTrue(parseNotNull("1.0.0").compareTo(parseNotNull("0.10.0")) > 0);
    assertTrue(parseNotNull("1.0.0").compareTo(parseNotNull("2.10.0")) < 0);

    assertTrue(parseNotNull("0.30.0").compareTo(parseNotNull("0.5.1000")) > 0);
    assertTrue(parseNotNull("0.30.10").compareTo(parseNotNull("0.100.0")) < 0);

    assertTrue(parseNotNull("2.9.123-test").compareTo(parseNotNull("2.9.100")) > 0);
    assertTrue(parseNotNull("2.9.123-test").compareTo(parseNotNull("2.9.124")) < 0);

    assertTrue(parseNotNull("11.123.0").compareTo(parseNotNull("11.123.0")) == 0);
  }

  @NotNull
  private static SemVer parseNotNull(@NotNull String text) {
    SemVer semVer = SemVer.parseFromText(text);
    assertNotNull(semVer);
    return semVer;
  }
}
