// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class FileUtilKtTest {
  @Test
  fun testEndsWithName() {
    assertThat(endsWithName("foo", "bar")).isFalse()
    assertThat(endsWithName("foo", "foo")).isTrue()
    assertThat(endsWithName("foo/bar", "foo")).isFalse()
    assertThat(endsWithName("foo/bar", "bar")).isTrue()
    assertThat(endsWithName("/foo", "foo")).isTrue()
    assertThat(endsWithName("fooBar", "Bar")).isFalse()
    assertThat(endsWithName("/foo/bar_bar", "bar")).isFalse()
  }
}