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

import org.junit.Assert;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SemVerTest {
  @Test
  public void parsing() {
    assertParsed("0.9.2", 0, 9, 2);
    assertParsed("0.9.2-", 0, 9, 2);
    assertParsed("0.9.2-dart", 0, 9, 2);
    assertParsed("4.0.0-alpha.1", 4, 0, 0);
    assertParsed("0.10.0-rc-1", 0, 10, 0);
    assertParsed("1.0.0-rc-1", 1, 0, 0);
    assertParsed("1.0.0-alpha", 1, 0, 0);
    assertParsed("1.0.0-0.3.7", 1, 0, 0);
    assertParsed("1.0.0-x.7.z.92", 1, 0, 0);

    assertNotParsed(null);
    assertNotParsed("");
    assertNotParsed("1.0.a");
    assertNotParsed("1.0");
    assertNotParsed("1..a");
  }

  @Test
  public void comparing() {
    assertThat(parse("1.0.0")).isGreaterThan(parse("0.10.0"));
    assertThat(parse("1.0.0")).isLessThan(parse("2.10.0"));

    assertThat(parse("0.30.0")).isGreaterThan(parse("0.5.1000"));
    assertThat(parse("0.30.10")).isLessThan(parse("0.100.0"));

    assertThat(parse("2.9.123-test")).isGreaterThan(parse("2.9.100"));
    assertThat(parse("2.9.123-test")).isLessThan(parse("2.9.124"));

    assertThat(parse("11.123.0")).isEqualTo(parse("11.123.0"));
    assertThat(parse("11.123.0")).isEqualByComparingTo(parse("11.123.0"));

    Assert.assertTrue(parse("4.12.5").isGreaterOrEqualThan(4, 12, 5));
    Assert.assertTrue(parse("4.12.5").isGreaterOrEqualThan(4, 12, 4));
    Assert.assertTrue(parse("4.12.5").isGreaterOrEqualThan(4, 11, 0));
    Assert.assertTrue(parse("4.12.5").isGreaterOrEqualThan(4, 11, 9));
    Assert.assertTrue(parse("4.12.5").isGreaterOrEqualThan(3, 100, 100));

    Assert.assertFalse(parse("4.12.5").isGreaterOrEqualThan(4, 12, 6));
    Assert.assertFalse(parse("4.12.5").isGreaterOrEqualThan(4, 13, 0));
    Assert.assertFalse(parse("4.12.5").isGreaterOrEqualThan(5, 1, 0));
  }

  private static void assertParsed(String version, int expectedMajor, int expectedMinor, int expectedPatch) {
    assertThat(parse(version)).isEqualTo(new SemVer(version, expectedMajor, expectedMinor, expectedPatch));
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