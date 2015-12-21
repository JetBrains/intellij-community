package com.intellij.util;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Version;
import junit.framework.TestCase;

import java.util.regex.Pattern;

public class VersionUtilTest extends TestCase {
  private static final Pattern[] VERSION_PATTERNS = {
    Pattern.compile("^GNU gdb ([\\d]+\\.[\\d]+\\.?[\\d]*)", Pattern.MULTILINE),
    Pattern.compile("^GNU gdb \\(GDB(?:;.*)?\\) ([\\d]+\\.[\\d]+(?:\\.[\\d]+)*).*", Pattern.MULTILINE),
    Pattern.compile("^java version \"([\\d]+\\.[\\d]+\\.[\\d]+)_[\\d]+\".*", Pattern.MULTILINE),
    Pattern.compile("^openjdk version \"([\\d]+\\.[\\d]+\\.[\\d]+)_[\\d]+.*\".*", Pattern.MULTILINE),
    Pattern.compile("^[a-zA-Z() \\d]*([\\d]+\\.[\\d]+\\.?[\\d]*).*", Pattern.MULTILINE)
  };
  private static final Pattern[] VERSION_UPDATE_PATTERNS = {
    Pattern.compile("^java version \"([\\d]+\\.[\\d]+\\.[\\d]+)_([\\d]+)\".*", Pattern.MULTILINE),
    Pattern.compile("^openjdk version \"([\\d]+\\.[\\d]+\\.[\\d]+)_([\\d]+).*\".*", Pattern.MULTILINE),
    Pattern.compile("^[a-zA-Z() \"\\d]*([\\d]+\\.[\\d]+\\.?[\\d]*).*", Pattern.MULTILINE)
  };

  private static final Object[][] testDataVersion = {
    {"GNU gdb 6.3.50-20050815 (Apple version gdb-1824) (Wed Feb  6 22:51:23 UTC 2013)", new Version(6, 3, 50)},
    {"GNU gdb (GDB) 7.6",                                                               new Version(7, 6, 0)},
    {"GNU gdb (GDB) 7.6something123",                                                   new Version(7, 6, 0)},
    {"GNU gdb (GDB; openSUSE 13.1) 7.6.50.20130731-cvs",                                new Version(7, 6, 50)},
    {"GNU gdb (GDB; devel:gcc) 7.8",                                                    new Version(7, 8, 0)},
    {"GNU gdb (GDB; devel:gcc) 7.8.55",                                                 new Version(7, 8, 55)},
    {"GNU gdb (GDB; devel:gcc) 7.8.55.123123-cvs",                                      new Version(7, 8, 55)},
    {"GNU gdb (GDB) Red Hat Enterprise Linux (7.2-60.el6_4.1)",                         new Version(7, 2, 0)},
    {"java version \"1.6.0_36\"",                                                       new Version(1, 6, 0)},
    {"java version \"1.7.0_85\"",                                                       new Version(1, 7, 0)},
    {"openjdk version \"1.8.0_45-internal\"",                                           new Version(1, 8, 0)},
    {"openjdk version \"1.8.1_60-release\"",                                            new Version(1, 8, 1)}
  };

  private static final Object[][] testDataVersionUpdate = {
    {"java version \"1.6.0\"",                                                          new Version(1, 6, 0), new Integer(0)},
    {"java version \"1.6.0_36\"",                                                       new Version(1, 6, 0), new Integer(36)},
    {"java version \"1.7.0_85\"",                                                       new Version(1, 7, 0), new Integer(85)},
    {"openjdk version \"1.8.0_45-internal\"",                                           new Version(1, 8, 0), new Integer(45)},
    {"openjdk version \"1.8.1_60-release\"",                                            new Version(1, 8, 1), new Integer(60)}
  };

  public void testParseVersion() throws Exception {
    for (Object[] aTestData : testDataVersion) {
      String versionString = (String)aTestData[0];
      assertEquals("For \"" + versionString + "\"", aTestData[1], VersionUtil.parseVersion(versionString, VERSION_PATTERNS));
    }
  }
  public void testParseVersionAndUpdate() throws Exception {
    for (Object[] aTestData : testDataVersionUpdate) {
      String versionString = (String)aTestData[0];
      Pair<Version, Integer> versionAndUpdate = VersionUtil.parseVersionAndUpdate(versionString, VERSION_UPDATE_PATTERNS);
      assertNotNull(versionAndUpdate);
      assertEquals("For \"" + versionString + "\"", aTestData[1], versionAndUpdate.first);
      assertEquals("For \"" + versionString + "\"", aTestData[2], versionAndUpdate.second);
    }
  }
}