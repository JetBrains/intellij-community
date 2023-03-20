// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InsecureHashBuilderTest {
  @Test
  fun `empty map`() {
    val builder = InsecureHashBuilder()
    builder.stringMap(mapOf())
    assertThat(builder.build()).isEqualTo(longArrayOf(5238470482016868669L))
  }

  @Test
  fun `string map`() {
    val builder = InsecureHashBuilder()
    builder.stringMap(mapOf("foo" to "bar"))
    assertThat(builder.build()).isEqualTo(longArrayOf(-2665511616818817272L, -4484417689633454546L, -3459221722170984053L))
  }

  @Test
  fun `string int map`() {
    val builder = InsecureHashBuilder()
    builder.stringIntMap(mapOf("foo" to 123, "bar" to 543))
    assertThat(builder.build()).isEqualTo(longArrayOf(8139571457004014537L, 3354812099852591849L, 3303764292165953682L))
  }

  @Test
  fun `several maps`() {
    val builder = InsecureHashBuilder()
    builder.stringMap(mapOf("foo" to "bar"))
    builder.stringIntMap(mapOf("foo" to 123, "bar" to 543))
    assertThat(builder.build()).isEqualTo(longArrayOf(-2665511616818817272L, -4484417689633454546L, -3459221722170984053L, 8139571457004014537L, 3354812099852591849L, 3303764292165953682L))
  }
}