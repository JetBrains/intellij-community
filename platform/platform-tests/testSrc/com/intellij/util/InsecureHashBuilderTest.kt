// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InsecureHashBuilderTest {
  @Test
  fun `empty map`() {
    val builder = InsecureHashBuilder()
    builder.stringMap(mapOf())
    assertThat(builder.build()).isEqualTo(longArrayOf(204526195655617521L))
  }

  @Test
  fun `string map`() {
    val builder = InsecureHashBuilder()
    builder.stringMap(mapOf("foo" to "bar"))
    assertThat(builder.build()).isEqualTo(longArrayOf(5110489462189080232L))
  }

  @Test
  fun `string int map`() {
    val builder = InsecureHashBuilder()
    builder.stringIntMap(mapOf("foo" to 123, "bar" to 543))
    assertThat(builder.build()).isEqualTo(longArrayOf(8624971787684361546L))
  }

  @Test
  fun `several maps`() {
    val builder = InsecureHashBuilder()
    builder.stringMap(mapOf("foo" to "bar"))
    builder.stringIntMap(mapOf("foo" to 123, "bar" to 543))
    assertThat(builder.build()).isEqualTo(longArrayOf(5110489462189080232L, 8624971787684361546L))
  }
}