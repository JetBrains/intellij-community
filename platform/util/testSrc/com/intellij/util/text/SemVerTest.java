/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SemVerTest {
  @Test
  public void parsing() {
    assertParsed("0.9.2", 0, 9, 2, null);
    assertParsed("0.9.2-", 0, 9, 2, "");
    assertParsed("0.9.2-dart", 0, 9, 2, "dart");
    assertParsed("4.0.0-alpha.1", 4, 0, 0, "alpha.1");
    assertParsed("0.10.0-rc-1", 0, 10, 0, "rc-1");
    assertParsed("1.0.0-rc-1", 1, 0, 0, "rc-1");
    assertParsed("1.0.0-alpha", 1, 0, 0, "alpha");
    assertParsed("1.0.0-0.3.7", 1, 0, 0, "0.3.7");
    assertParsed("1.0.0-x.7.z.92", 1, 0, 0, "x.7.z.92");

    assertNotParsed(null);
    assertNotParsed("");
    assertNotParsed("1.0.a");
    assertNotParsed("1.0");
    assertNotParsed("1..a");
  }

  @Test
  public void comparing() {
    assertThat(parse("11.123.0")).isEqualTo(parse("11.123.0"));
    assertThat(parse("11.123.0")).isEqualByComparingTo(parse("11.123.0"));

    assertThat(parse("11.123.0-a.b.c-1")).isEqualTo(parse("11.123.0-a.b.c-1"));
    assertThat(parse("11.123.0-a.b.c-1")).isEqualByComparingTo(parse("11.123.0-a.b.c-1"));

    assertPrecedence("0.10.0", "1.0.0");
    assertPrecedence("1.0.0", "2.10.0");
    assertPrecedence("0.5.1000", "0.30.0");
    assertPrecedence("0.30.10", "0.100.0");

    assertPrecedence("2.9.100", "2.9.123-test");
    assertPrecedence("2.9.123-test", "2.9.124");
    assertPrecedence("2.9.123", "2.9.124-test");

    assertPrecedence("1.2.3-a", "1.2.3");

    assertPrecedence("1.2.3-12", "1.2.3-a");
    assertPrecedence("1.2.3-22", "1.2.3-100");
    assertPrecedence("1.2.3-22", "1.2.3-31");
    assertPrecedence("1.2.3-22", "1.2.3-222");

    assertPrecedence("1.2.3-a.b.c", "1.2.3-a.b.d");
    assertPrecedence("1.2.3-a.b.c", "1.2.3-a.b.c.a");

    assertPrecedence("1.2.3-a.b.1", "1.2.3-a.b.c");
    assertPrecedence("1.2.3-a.b.1", "1.2.3-a.c.1");
    assertPrecedence("1.2.3-a.cbc.100", "1.2.3-a.cca.1");
    assertPrecedence("1.2.3-a.cb.1", "1.2.3-a.cba.1");

    // Example from SemVer documentation https://semver.org/#spec-item-11
    assertPrecedence("1.0.0-alpha", "1.0.0-alpha.1");
    assertPrecedence("1.0.0-alpha.1", "1.0.0-alpha.beta");
    assertPrecedence("1.0.0-alpha.beta", "1.0.0-beta");
    assertPrecedence("1.0.0-beta", "1.0.0-beta.2");
    assertPrecedence("1.0.0-beta.2", "1.0.0-beta.11");
    assertPrecedence("1.0.0-beta.11", "1.0.0-rc.1");
    assertPrecedence("1.0.0-rc.1", "1.0.0");

    Assert.assertTrue(parse("4.12.5").isGreaterOrEqualThan(4, 12, 5));
    Assert.assertTrue(parse("4.12.5-a").isGreaterOrEqualThan(4, 12, 5));
    Assert.assertTrue(parse("4.12.5").isGreaterOrEqualThan(4, 12, 4));
    Assert.assertTrue(parse("4.12.5-a").isGreaterOrEqualThan(4, 12, 4));
    Assert.assertTrue(parse("4.12.5").isGreaterOrEqualThan(4, 11, 0));
    Assert.assertTrue(parse("4.12.5").isGreaterOrEqualThan(4, 11, 9));
    Assert.assertTrue(parse("4.12.5").isGreaterOrEqualThan(3, 100, 100));

    Assert.assertFalse(parse("4.12.5").isGreaterOrEqualThan(4, 12, 6));
    Assert.assertFalse(parse("4.12.5-a").isGreaterOrEqualThan(4, 12, 6));
    Assert.assertFalse(parse("4.12.5").isGreaterOrEqualThan(4, 13, 0));
    Assert.assertFalse(parse("4.12.5").isGreaterOrEqualThan(5, 1, 0));
  }

  private static void assertPrecedence(String lesserVersion, String higherVersion) {
    SemVer v1 = parse(lesserVersion);
    SemVer v2 = parse(higherVersion);
    assertThat(v1).isLessThan(v2);
    assertThat(v2).isGreaterThan(v1);
    assertThat(v1).isNotEqualByComparingTo(v2);
    assertThat(v1).isNotEqualTo(v2);
  }

  private static void assertParsed(@NotNull String version,
                                   int expectedMajor,
                                   int expectedMinor,
                                   int expectedPatch,
                                   @Nullable String expectedPreRelease) {
    assertThat(parse(version)).isEqualTo(new SemVer(version, expectedMajor, expectedMinor, expectedPatch, expectedPreRelease));
  }

  private static void assertNotParsed(String version) {
    assertThat(SemVer.parseFromText(version)).isNull();
  }

  private static SemVer parse(String text) {
    SemVer semVer = SemVer.parseFromText(text);
    assertThat(semVer).describedAs(text).isNotNull();
    return semVer;
  }
}
