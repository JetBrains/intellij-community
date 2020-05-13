// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.util.text.VersionComparatorUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VersionCompareTest {
  @Test
  public void testEqual() {
    String v1 = "0.0.1";
    String v2 = "0.0.1";
    assertEquals("Version is not equal", 0,
                 compareVersions(v1, v2));
  }

  @Test
  public void testGreat() {
    String v1 = "0.0.2";
    String v2 = "0.0.1";
    assertTrue("Version 1 is not great than Version 2",
               compareVersions(v1, v2) > 0);
  }

  @Test
  public void testLess() {
    String v1 = "0.0.1";
    String v2 = "0.0.2";
    assertTrue("Version 1 is not less than Version 2",
               compareVersions(v1, v2) < 0);
  }

  @Test
  public void testGreatDiff() {
    String v1 = "0.0.2.0";
    String v2 = "0.0.1.0";
    assertTrue("Version 1 is not great than Version 2",
               compareVersions(v1, v2) > 0);
  }

  @Test
  public void testLessDiff() {
    String v1 = "0.0.1.1";
    String v2 = "0.0.2.0";
    assertTrue("Version 1 is not less than Version 2",
               compareVersions(v1, v2) < 0);
  }

  @Test
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

    assertEquals(0, compareVersions("1.0.00", "1.0"));
    assertTrue(compareVersions("1.0.01", "1") > 0);
  }

  @Test
  public void testWord() {
    String v1 = "0.0.1a";
    String v2 = "0.0.2";
    assertTrue("Version 1 is not less than Version 2",
               compareVersions(v1, v2) < 0);
  }

  @Test
  public void testNewest() {
    String serverVer = "1.0.10";
    String userVer = "1.0.9";
    assertTrue("Server version is not great than user version",
               compareVersions(serverVer, userVer) > 0);
  }

  @Test
  public void testJira() {
    assertTrue(compareVersions("3.6.2", "3.7") < 0);
    assertTrue(compareVersions("3.7.1", "3.13.4") < 0);
  }

  /* http://semver.org */
  @Test
  public void testSemVer() {
    ascending("0.1.2", "0.1.11", "0.1.11-2", "1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-beta",
              "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0");
  }

  @Test
  public void testPythonPlugin() {
    ascending("3.1 Beta", "3.1 Beta 2", "3.1", "3.1.1.134.1462", "3.4.Beta.135.1",
              "3.4.135.21", "4.0 Beta 139.3", "4.0 Beta 139.58", "4.0.12",
              "4.1 141.4 EAP", "4.1 141.39 EAP", "4.5 141.82");
    ascending("4.1", "142.175 v4.5", "142.175 v4.5.1");
  }

  @Test
  public void testRubyPlugin() {
    assertTrue(compareVersions("7.1.0.20150520", "7.1.0.20150501") > 0);
  }

  // Some real world examples from IDEA plugin repository

  @Test
  public void testScalaPlugin() {
    assertTrue(compareVersions("1.3.3.16-14.1", "1.3.3.15-14.1") > 0);
  }

  @Test
  public void testBashPlugin() {
    assertTrue(compareVersions("1.1beta16", "1.1beta8") > 0);
    assertTrue(compareVersions("1.5.0.142-beta2", "1.5.0.142-beta1") > 0);
  }

  @Test
  public void testKotlinPlugin() {
    ascending("1.3-M2", "1.3.0-rc");
    ascending("1.3-M2", "1.3.0-dev");
  }

  private static int compareVersions(String v1, String v2) {
    return VersionComparatorUtil.compare(v1, v2);
  }

  private static void ascending(String... versions) {
    assertTrue(versions.length > 1);
    for (int i = 0; i < versions.length - 1; i++) {
      assertTrue(versions[i] + " is greater then " + versions[i + 1],
                 compareVersions(versions[i], versions[i + 1]) < 0);
    }
  }
}