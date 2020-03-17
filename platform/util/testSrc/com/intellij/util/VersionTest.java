// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.Version;
import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

public class VersionTest {
  @Test
  public void testParseVersion() {
    assertParsed("0", 0, 0, 0);
    assertParsed("0.0", 0, 0, 0);
    assertParsed("0.0.0", 0, 0, 0);
    assertParsed("0.0.0-ALPHA", 0, 0, 0);

    assertParsed("1", 1, 0, 0);
    assertParsed("1.2", 1, 2, 0);
    assertParsed("1.2.3", 1, 2, 3);
    assertParsed("1.2.3.4", 1, 2, 3);

    assertParsed("1beta", 1, 0, 0);
    assertParsed("1.2beta", 1, 2, 0);
    assertParsed("1.2.3beta", 1, 2, 3);
    assertParsed("1.2.3.4beta", 1, 2, 3);

    assertParsed("1-beta", 1, 0, 0);
    assertParsed("1.2-beta", 1, 2, 0);
    assertParsed("1.2.3-beta", 1, 2, 3);
    assertParsed("1.2.3.4-beta", 1, 2, 3);

    assertParsed("1.beta", 1, 0, 0);
    assertParsed("1.2.beta", 1, 2, 0);
    assertParsed("1.2.3.beta", 1, 2, 3);

    assertNotParsed("");
    assertNotParsed("beta1");
    assertNotParsed("beta.beta.beta");
  }

  @Test
  public void testVersion() {
    Version v = new Version(3, 2, 1);

    assertTrue(v.is(3));
    assertFalse(v.is(4));

    assertTrue(v.is(3, 2));
    assertFalse(v.is(3, 3));

    assertTrue(v.is(3, 2, 1));
    assertFalse(v.is(3, 2, 2));

    assertTrue(v.compareTo(3) == 0);
    assertTrue(v.compareTo(4) < 0);
    assertTrue(v.compareTo(2) > 0);
    assertTrue(v.compareTo(3, 2) == 0);
    assertTrue(v.compareTo(3, 3) < 0);
    assertTrue(v.compareTo(3, 1) > 0);
    assertTrue(v.compareTo(3, 2, 1) == 0);
    assertTrue(v.compareTo(3, 2, 2) < 0);
    assertTrue(v.compareTo(3, 2, 0) > 0);
  }

  private static void assertParsed(String text, int major, int minor, int patch) {
    assertThat(Version.parseVersion(text)).describedAs(text).isEqualTo(new Version(major, minor, patch));
  }

  private static void assertNotParsed(String text) {
    assertThat(Version.parseVersion(text)).describedAs(text).isNull();
  }
}