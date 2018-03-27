package com.intellij.util;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionUtilTest extends TestCase {
  private static final Pattern[] VERSION_PATTERNS = {
    Pattern.compile("^GNU gdb ([\\d]+\\.[\\d]+\\.?[\\d]*)", Pattern.MULTILINE),
    Pattern.compile("^GNU gdb \\(GDB(?:;.*)?\\) ([\\d]+\\.[\\d]+(?:\\.[\\d]+)*).*", Pattern.MULTILINE),
    Pattern.compile("^java version \"([\\d]+\\.[\\d]+\\.[\\d]+)_[\\d]+\".*", Pattern.MULTILINE),
    Pattern.compile("^openjdk version \"([\\d]+\\.[\\d]+\\.[\\d]+)_[\\d]+.*\".*", Pattern.MULTILINE),
    Pattern.compile("^[a-zA-Z() \\d]*([\\d]+\\.[\\d]+\\.?[\\d]*).*", Pattern.MULTILINE)
  };
  @NotNull
  private static final Map<Pattern, Function<Matcher, Pair<Version, Integer>>> LINE_TO_VERSION_PATTERNS = ContainerUtil.newLinkedHashMap(
    Pair.create(Pattern.compile("^java version \"1\\.([\\d]+)\\.([\\d]+)_([\\d]+).*\".*", Pattern.MULTILINE), matcher -> Pair.create(new Version(1, StringUtil.parseInt(matcher.group(1),0), StringUtil.parseInt(matcher.group(2),0)), StringUtil.parseInt(matcher.group(3),0))),
    Pair.create(Pattern.compile("^java version \"9\\.([\\d]+)\\.([\\d]+)_([\\d]+).*\".*", Pattern.MULTILINE), matcher -> Pair.create(new Version(9, StringUtil.parseInt(matcher.group(1),0), StringUtil.parseInt(matcher.group(2),0)), StringUtil.parseInt(matcher.group(3),0))),
    Pair.create(Pattern.compile("^java version \"9-ea.*\".*", Pattern.MULTILINE), matcher -> Pair.create(new Version(9, 0, 0), 0)),
    Pair.create(Pattern.compile("^openjdk version \"9-internal.*\".*", Pattern.MULTILINE), matcher -> Pair.create(new Version(9, 0, 0), 0)),
    Pair.create(Pattern.compile("^openjdk version \"1\\.([\\d]+)\\.([\\d]+)_([\\d]+).*\".*", Pattern.MULTILINE), matcher -> Pair.create(new Version(1, StringUtil.parseInt(matcher.group(1),0), StringUtil.parseInt(matcher.group(2),0)), StringUtil.parseInt(matcher.group(3),0))),
    Pair.create(Pattern.compile("^openjdk version \"9-ea.*\".*", Pattern.MULTILINE), matcher -> Pair.create(new Version(9, 0, 0), 0)),
    Pair.create(Pattern.compile("^[a-zA-Z() \"\\d]*([\\d]+)\\.([\\d]+)\\.?([\\d]*).*", Pattern.MULTILINE), matcher -> Pair.create(new Version(StringUtil.parseInt(matcher.group(1),0), StringUtil.parseInt(matcher.group(2),0), StringUtil.parseInt(matcher.group(3),0)), 0))
  );

  @NotNull
  private static final Map<Pattern, Function<Matcher, Pair<Version, Integer>>> PROP_TO_VERSION_PATTERNS = ContainerUtil.newLinkedHashMap(
    Pair.create(Pattern.compile("1\\.([\\d]+)\\.([\\d]+)_([\\d]+).*", Pattern.MULTILINE), matcher -> Pair.create(new Version(1, StringUtil.parseInt(matcher.group(1),0), StringUtil.parseInt(matcher.group(2),0)), StringUtil.parseInt(matcher.group(3),0))),
    Pair.create(Pattern.compile("9\\.([\\d]+)\\.([\\d]+)_([\\d]+).*", Pattern.MULTILINE), matcher -> Pair.create(new Version(9, StringUtil.parseInt(matcher.group(1),0), StringUtil.parseInt(matcher.group(2),0)), StringUtil.parseInt(matcher.group(3),0))),
    Pair.create(Pattern.compile("9-ea.*", Pattern.MULTILINE), matcher -> Pair.create(new Version(9, 0, 0), 0)),
    Pair.create(Pattern.compile("9-internal.*", Pattern.MULTILINE), matcher -> Pair.create(new Version(9, 0, 0), 0)),
    Pair.create(Pattern.compile("1\\.([\\d]+)\\.([\\d]+)_([\\d]+).*", Pattern.MULTILINE), matcher -> Pair.create(new Version(1, StringUtil.parseInt(matcher.group(1),0), StringUtil.parseInt(matcher.group(2),0)), StringUtil.parseInt(matcher.group(3),0))),
    Pair.create(Pattern.compile("9-ea.*", Pattern.MULTILINE), matcher -> Pair.create(new Version(9, 0, 0), 0)),
    Pair.create(Pattern.compile("([\\d]+)\\.([\\d]+)\\.?([\\d]*).*", Pattern.MULTILINE), matcher -> Pair.create(new Version(StringUtil.parseInt(matcher.group(1),0), StringUtil.parseInt(matcher.group(2),0), StringUtil.parseInt(matcher.group(3),0)), 0))
  );


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
    {"java version \"1.6.0\"",                                                          new Version(1, 6, 0), 0},
    {"java version \"1.6.0_36\"",                                                       new Version(1, 6, 0), 36},
    {"java version \"1.7.0_85\"",                                                       new Version(1, 7, 0), 85},
    {"java version \"1.8.0_122-ea\"",                                                   new Version(1, 8, 0), 122},
    {"openjdk version \"1.8.0_45-internal\"",                                           new Version(1, 8, 0), 45},
    {"openjdk version \"1.8.1_60-release\"",                                            new Version(1, 8, 1), 60},
    {"openjdk version \"1.8.0_121-2-ojdkbuild\"",                                       new Version(1, 8, 0), 121},
    {"openjdk version \"9-ea\"",                                                        new Version(9, 0, 0), 0},
    {"openjdk version \"9-internal\"",                                                  new Version(9, 0, 0), 0},
    {"java version \"9-ea\"",                                                           new Version(9, 0, 0), 0},
    {"java version \"9.0.1\"",                                                          new Version(9, 0, 1), 0}
  };

  private static final Object[][] testDataPropVersionUpdate = {
    {"1.6.0",                                                                           new Version(1, 6, 0), 0},
    {"1.6.0_36",                                                                        new Version(1, 6, 0), 36},
    {"1.7.0_85",                                                                        new Version(1, 7, 0), 85},
    {"1.8.0_122-ea",                                                                    new Version(1, 8, 0), 122},
    {"1.8.0_45-internal",                                                               new Version(1, 8, 0), 45},
    {"1.8.1_60-release",                                                                new Version(1, 8, 1), 60},
    {"1.8.0_121-2-ojdkbuild",                                                           new Version(1, 8, 0), 121},
    {"9-ea",                                                                            new Version(9, 0, 0), 0},
    {"9-internal",                                                                      new Version(9, 0, 0), 0},
    {"9-ea",                                                                            new Version(9, 0, 0), 0},
    {"9.0.1",                                                                           new Version(9, 0, 1), 0}
  };

  public void testParseVersion() {
    for (Object[] aTestData : testDataVersion) {
      String versionString = (String)aTestData[0];
      assertEquals("For \"" + versionString + "\"", aTestData[1], VersionUtil.parseVersion(versionString, VERSION_PATTERNS));
    }
  }

  public void testParseVersionAndUpdate() {
    for (Pair<Map<Pattern, Function<Matcher, Pair<Version, Integer>>>, Object[][]> p : Arrays
      .asList(Pair.create(LINE_TO_VERSION_PATTERNS, testDataVersionUpdate),
              Pair.create(PROP_TO_VERSION_PATTERNS, testDataPropVersionUpdate))) {
      for (Object[] aTestData : p.second) {
        String versionString = (String)aTestData[0];
        Pair<Version, Integer> versionAndUpdate = VersionUtil.parseNewVersionAndUpdate(versionString, p.first);
        assertNotNull(versionAndUpdate);
        assertEquals("For \"" + versionString + "\"", aTestData[1], versionAndUpdate.first);
        assertEquals("For \"" + versionString + "\"", aTestData[2], versionAndUpdate.second);
      }
    }
  }
}