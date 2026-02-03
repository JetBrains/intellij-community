package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtil;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.Collection;

public class VersionComparatorTest extends TestCase {
  public void testNulls() {
    assertVerGreater("a", null);
    assertVerLess(null, "null");
    assertVerEquals(null, null);
  }

  public void testSplit() {
    assertStrsEquals(new String[]{"a", "b"}, VersionComparatorUtil.splitVersionString("a b"));
    assertStrsEquals(new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "#ab"},
                     VersionComparatorUtil.splitVersionString("1(2)3.4.5_6;7:/8,9 10+11~12#ab"));
    assertStrsEquals(new String[]{"ab", "12", "ba", "6"}, VersionComparatorUtil.splitVersionString("ab12ba6"));
    assertStrsEquals(new String[]{"ab", "12", "ba"}, VersionComparatorUtil.splitVersionString("ab12ba"));
    assertStrsEquals(new String[]{"12", "ba"}, VersionComparatorUtil.splitVersionString("12ba"));
    assertStrsEquals(new String[]{"12", "ba", "9"}, VersionComparatorUtil.splitVersionString("12ba9"));
    assertStrsEquals(new String[]{"1", "0", "RC", "2"}, VersionComparatorUtil.splitVersionString("1.0RC2"));
    assertStrsEquals(new String[]{"1", "0", "M", "1"}, VersionComparatorUtil.splitVersionString("1.0M1"));
    assertStrsEquals(new String[]{"000123456789"}, VersionComparatorUtil.splitVersionString("000123456789"));
    assertStrsEquals(new String[]{""}, VersionComparatorUtil.splitVersionString(""));
  }

  public void testExamples() {
    assertVerEquals("1", "1");
    assertVerLess("1", "2");

    assertVerEquals("1.0.", "1.0");
    assertVerLess("1.0", "2.0");
    assertVerGreater("1.2", "1.02");
    assertVerGreater("1.1", "1.02");
    assertVerLess("1.1e", "1.1f");
    assertVerGreater("1.1", "1.02");
    assertVerGreater("1.01", "1.002");
    assertVerLess("1.01", "1.02");
    assertVerLess("1.35", "1.36");
    assertVerGreater("2.35", "1.36");

    assertVerLess("1.0rc1", "1.0release");
    assertVerGreater("1.0", "1.0rc");
    assertVerGreater("1.0.1", "1.0sp3");
    assertVerLess("1.02", "1.12");
    assertVerGreater("1.0sp", "1.0");
    assertVerLess("1.0bred", "1.0.1");
    assertVerEquals("1.3.0", "1.3");

    assertVerLess("r.1", "r.666");
    assertVerGreater("1.6-beta-1", "1.5.6");
    assertVerLess("2.7.1.final", "2.7.2.rc1");
    assertVerGreater("2.7.1.final", "2.7.1.rc1");
    assertVerLess("1.0M1", "1.0RC2");

    assertVerGreater(
        "11111111111111111111111111111111111111111111111111111111111111111111111111111111111111111112",
        "11111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111");
  }

  public void testFormal() {
    assertVerEquals("7-snapshot", "7-sNaP");
    assertVerEquals("7-alpha", "7-a");
    assertVerEquals("7-beta", "7-b");
    assertVerEquals("7-rel", "7-release");
    assertVerEquals("7-rel", "7-r");
    assertVerEquals("7-rel", "7-final");

    assertVerLess("snapshot", "m");
    assertVerLess("m", "eap");
    assertVerLess("eap", "alpha");
    assertVerLess("alpha", "beta");
    assertVerLess("beta", "rc");
    assertVerLess("rc", "");
    assertVerLess("", "sp");
    assertVerLess("sp", "release");
    assertVerLess("release", "trash");
    assertVerLess("trash", "1");
    assertVerLess("preview", "p");
  }

  private void assertStrsEquals(String[] expected, Collection<String> actual) {
    Assert.assertEquals(StringUtil.join(expected, "^"), StringUtil.join(actual, "^"));
  }

  private void assertVerEquals(final String v1, final String v2) {
    assertEquals(0, VersionComparatorUtil.compare(v1, v2));
  }

  private void assertVerLess(final String v1, final String v2) {
    assertTrue(VersionComparatorUtil.compare(v1, v2) < 0);
    assertTrue(VersionComparatorUtil.compare(v2, v1) > 0);

    assertVerEquals(v1, v1);
    assertVerEquals(v2, v2);
  }

  private void assertVerGreater(final String v1, final String v2) {
    assertTrue(VersionComparatorUtil.compare(v1, v2) > 0);
    assertTrue(VersionComparatorUtil.compare(v2, v1) < 0);

    assertVerEquals(v1, v1);
    assertVerEquals(v2, v2);
  }
}

