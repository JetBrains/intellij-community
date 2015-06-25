/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.plugins;

import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.util.text.StringUtil;
import junit.framework.TestCase;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 13, 2003
 * Time: 8:19:03 PM
 * To change this template use Options | File Templates.
 */
public class VersionCompareTest extends TestCase {
  private static int compareVersions(String v1, String v2) {
    return PluginDownloader.comparePluginVersions(v1, v2);
  }

  public void testEqual () {
    String v1 = "0.0.1";
    String v2 = "0.0.1";

    assertTrue("Version is not equal", compareVersions(v1, v2) == 0);
  }

  public void testGreat () {
    String v1 = "0.0.2";
    String v2 = "0.0.1";

    assertTrue("Version 1 is not great than Version 2",
               compareVersions(v1, v2) > 0);
  }

  public void testLess () {
    String v1 = "0.0.1";
    String v2 = "0.0.2";

    assertTrue("Version 1 is not less than Version 2",
               compareVersions(v1, v2) < 0);
  }

  public void testGreatDiff () {
    String v1 = "0.0.2.0";
    String v2 = "0.0.1.0";

    assertTrue("Version 1 is not great than Version 2",
               compareVersions(v1, v2) > 0);
  }

  public void testLessDiff () {
    String v1 = "0.0.1.1";
    String v2 = "0.0.2.0";

    assertTrue("Version 1 is not less than Version 2",
               compareVersions(v1, v2) < 0);
  }

  public void testDifferentNumberOfParts() {
    assertEquals(0, compareVersions("1.0.0", "1.0"));
    assertEquals(0, compareVersions("1.0.0", "1"));

    assertEquals(0, compareVersions("1.0.0", "1.0."));
    assertEquals(0, compareVersions("1.0.0", "1."));
    
    assertEquals(0, compareVersions("1.0.", "1.0.0"));
    assertEquals(0, compareVersions("1.", "1.0.0"));

    assertEquals(0, compareVersions("1.0", "1.0.0"));
    assertEquals(0, compareVersions("1", "1.0.0"));

    assertEquals(1, compareVersions("1.0.1", "1.0"));
    assertEquals(1, compareVersions("1.0.1", "1"));
    assertEquals(-1, compareVersions("1.0", "1.0.1"));
    assertEquals(-1, compareVersions("1", "1.0.1"));

    assertTrue(compareVersions("1.0.a", "1.0") > 0);
    assertTrue(compareVersions("1.0.a", "1") > 0);
    assertTrue(compareVersions("1.0", "1.0.a") < 0);
    assertTrue(compareVersions("1", "1.0.a") < 0);

    assertTrue(compareVersions("1.0.+", "1.0") > 0);
    assertTrue(compareVersions("1.0", "1.0.+") < 0);

    assertTrue(compareVersions("1.0.00", "1.0") == 0);
    assertTrue(compareVersions("1.0.01", "1") > 0);
  }

  public void testWord () {
    String v1 = "0.0.1a";
    String v2 = "0.0.2";

    assertTrue("Version 1 is not less than Version 2",
               compareVersions(v1, v2) < 0);
  }

  public void testNewest () {
    String serverVer = "1.0.10";
    String userVer = "1.0.9";

    assertTrue("Server version is not great than user version",
               compareVersions(serverVer, userVer) > 0);
  }

  public void testJira() {
    assertTrue(compareVersions("3.6.2", "3.7") < 0);
    assertTrue(compareVersions("3.7.1", "3.13.4") < 0);
  }
}

