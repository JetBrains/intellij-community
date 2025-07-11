// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.util.Version;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class VersionTest {
  @Test void testParseVersion() {
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

  @Test void testVersion() {
    var v = new Version(3, 2, 1);

    assertTrue(v.is(3));
    assertFalse(v.is(4));

    assertTrue(v.is(3, 2));
    assertFalse(v.is(3, 3));

    assertTrue(v.is(3, 2, 1));
    assertFalse(v.is(3, 2, 2));

    assertEquals(0, v.compareTo(3));
    assertTrue(v.compareTo(4) < 0);
    assertTrue(v.compareTo(2) > 0);
    assertEquals(0, v.compareTo(3, 2));
    assertTrue(v.compareTo(3, 3) < 0);
    assertTrue(v.compareTo(3, 1) > 0);
    assertEquals(0, v.compareTo(3, 2, 1));
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
