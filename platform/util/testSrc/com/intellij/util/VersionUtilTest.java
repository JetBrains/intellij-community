// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.Version;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class VersionUtilTest {
  private static final Pattern[] VERSION_PATTERNS = {
    Pattern.compile("^GNU gdb ([\\d]+\\.[\\d]+\\.?[\\d]*)", Pattern.MULTILINE),
    Pattern.compile("^GNU gdb \\(GDB(?:;.*)?\\) ([\\d]+\\.[\\d]+(?:\\.[\\d]+)*).*", Pattern.MULTILINE),
    Pattern.compile("^[a-zA-Z() \\d]*([\\d]+\\.[\\d]+\\.?[\\d]*).*", Pattern.MULTILINE)
  };

  private static final Object[][] testDataVersion = {
    {"GNU gdb 6.3.50-20050815 (Apple version gdb-1824) (Wed Feb  6 22:51:23 UTC 2013)", new Version(6, 3, 50)},
    {"GNU gdb (GDB) 7.6",                                                               new Version(7, 6, 0)},
    {"GNU gdb (GDB) 7.6something123",                                                   new Version(7, 6, 0)},
    {"GNU gdb (GDB; openSUSE 13.1) 7.6.50.20130731-cvs",                                new Version(7, 6, 50)},
    {"GNU gdb (GDB; devel:gcc) 7.8",                                                    new Version(7, 8, 0)},
    {"GNU gdb (GDB; devel:gcc) 7.8.55",                                                 new Version(7, 8, 55)},
    {"GNU gdb (GDB; devel:gcc) 7.8.55.123123-cvs",                                      new Version(7, 8, 55)},
    {"GNU gdb (GDB) Red Hat Enterprise Linux (7.2-60.el6_4.1)",                         new Version(7, 2, 0)}
  };

  @Parameterized.Parameters(name = "{0}")
  public static Object[][] testData() {
    return testDataVersion;
  }

  @Parameterized.Parameter public String input;
  @Parameterized.Parameter(1) public Version expected;

  @Test
  public void testParseVersion() {
    assertEquals(expected, VersionUtil.parseVersion(input, VERSION_PATTERNS));
  }
}