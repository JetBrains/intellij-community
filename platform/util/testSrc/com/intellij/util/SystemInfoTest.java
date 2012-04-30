/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.util.SystemInfo;
import junit.framework.TestCase;

public class SystemInfoTest extends TestCase {
  public void testMacOsVersions() throws Exception {
    // see http://developer.apple.com/library/mac/#documentation/Carbon/Reference/Gestalt_Manager/Reference/reference.html#//apple_ref/doc/uid/TP30000036-CH1g-F01632
    // System Version Selectors

    assertEquals("1.2", SystemInfo.getMacOSMajorVersion("1.2.3"));
    assertEquals("20.30", SystemInfo.getMacOSMajorVersion("20.30.40"));

    assertEquals("1.0", SystemInfo.getMacOSMajorVersion("1"));
    assertEquals("1.2", SystemInfo.getMacOSMajorVersion("1.2"));
    assertEquals("1.2", SystemInfo.getMacOSMajorVersion("1.2.3.4"));
  }

  public void testMacOsVersionCode() throws Exception {
    // see http://developer.apple.com/library/mac/#documentation/Carbon/Reference/Gestalt_Manager/Reference/reference.html#//apple_ref/doc/uid/TP30000036-CH1g-F01632
    // System Version Selectors

    assertVersions("1.1.1", "0111", "0110", "0101");
    assertVersions("10.5.2", "1052", "1050", "0502");
    assertVersions("10.15.15", "1099", "1090", "1515");

    assertVersions("", "0000", "0000", "0000");
    assertVersions("1.2", "0120", "0120", "0200");
    assertVersions("1.2.3.4", "0123", "0120", "0203");
    assertVersions("a.b.c", "0000", "0000", "0000");
  }

  private void assertVersions(String version, String full, String major, String minor) {
    assertEquals(full, SystemInfo.getMacOSVersionCode(version));
    assertEquals(major, SystemInfo.getMacOSMajorVersionCode(version));
    assertEquals(minor, SystemInfo.getMacOSMinorVersionCode(version));
  }
}
