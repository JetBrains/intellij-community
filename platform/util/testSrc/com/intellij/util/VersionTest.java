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
package com.intellij.util;

import com.intellij.openapi.util.Version;
import org.junit.Test;

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

  private static void assertParsed(String text, int major, int minor, int patch) {
    assertThat(Version.parseVersion(text)).describedAs(text).isEqualTo(new Version(major, minor, patch));
  }

  private static void assertNotParsed(String text) {
    assertThat(Version.parseVersion(text)).describedAs(text).isNull();
  }
}