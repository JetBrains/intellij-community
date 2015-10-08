/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.util.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

private class FileUtilKtTest {
  @Test fun testEndsWithName() {
    assertThat(endsWithName("foo", "bar")).isFalse()
    assertThat(endsWithName("foo", "foo")).isTrue()
    assertThat(endsWithName("foo/bar", "foo")).isFalse()
    assertThat(endsWithName("foo/bar", "bar")).isTrue()
    assertThat(endsWithName("/foo", "foo")).isTrue()
    assertThat(endsWithName("fooBar", "Bar")).isFalse()
    assertThat(endsWithName("/foo/bar_bar", "bar")).isFalse()
  }
}