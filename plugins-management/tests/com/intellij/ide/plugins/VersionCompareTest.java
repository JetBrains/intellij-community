package com.intellij.ide.plugins;

import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 13, 2003
 * Time: 8:19:03 PM
 * To change this template use Options | File Templates.
 */
public class VersionCompareTest extends TestCase {
  public static TestSuite suite () {
    return new TestSuite (VersionCompareTest.class);
  }

  public void testEqual () {
    String v1 = "0.0.1";
    String v2 = "0.0.1";

    assertTrue("Version is not equal", IdeaPluginDescriptorImpl.compareVersion(v1, v2) == 0);
  }

  public void testGreat () {
    String v1 = "0.0.2";
    String v2 = "0.0.1";

    assertTrue("Version 1 is not great than Version 2",
               IdeaPluginDescriptorImpl.compareVersion(v1, v2) > 0);
  }

  public void testLess () {
    String v1 = "0.0.1";
    String v2 = "0.0.2";

    assertTrue("Version 1 is not less than Version 2",
               IdeaPluginDescriptorImpl.compareVersion(v1, v2) < 0);
  }

  public void testGreatDiff () {
    String v1 = "0.0.2.0";
    String v2 = "0.0.1.0";

    assertTrue("Version 1 is not great than Version 2",
               IdeaPluginDescriptorImpl.compareVersion(v1, v2) > 0);
  }

  public void testLessDiff () {
    String v1 = "0.0.1.1";
    String v2 = "0.0.2.0";

    assertTrue("Version 1 is not less than Version 2",
               IdeaPluginDescriptorImpl.compareVersion(v1, v2) < 0);
  }

  public void testWord () {
    String v1 = "0.0.1a";
    String v2 = "0.0.2";

    assertTrue("Version 1 is not less than Version 2",
               IdeaPluginDescriptorImpl.compareVersion(v1, v2) < 0);
  }

  public void testNewest () {
    String serverVer = "1.0.10";
    String userVer = "1.0.9";

    assertTrue("Server version is not great than user version",
               IdeaPluginDescriptorImpl.compareVersion(serverVer, userVer) > 0);
  }
}

