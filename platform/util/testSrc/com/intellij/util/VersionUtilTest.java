// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.Version;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

  @ParameterizedTest(name = "{0}")
  @MethodSource("testData")
  public void testParseVersion(String input, Version expected) {
    assertEquals(expected, VersionUtil.parseVersion(input, VERSION_PATTERNS));
  }

  public static Stream<Arguments> testData() {
    return Arrays.stream(testDataVersion).map(Arguments::of);
  }
}