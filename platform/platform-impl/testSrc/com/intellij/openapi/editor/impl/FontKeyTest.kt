// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.font.FontRenderContext

internal class FontKeyTest {
  @Test
  fun `keys with equal variant sets in another order are equal`() {
    val key = FontKey(13f, false, linkedSetOf("ss01", "zero"), null)
    val reordered = FontKey(13f, false, linkedSetOf("zero", "ss01"), null)
    assertThat(reordered).isEqualTo(key)
    assertThat(reordered.hashCode()).isEqualTo(key.hashCode())
    assertThat(key.matches(13f, false, linkedSetOf("zero", "ss01"), null)).isTrue()
  }

  @Test
  fun `key does not match other arguments`() {
    val context = FontRenderContext(null, true, true)
    val key = FontKey(13f, false, setOf("ss01"), context)
    assertThat(key.matches(13f, false, setOf("ss01"), FontRenderContext(null, true, true))).isTrue()
    assertThat(key.matches(14f, false, setOf("ss01"), context)).isFalse()
    assertThat(key.matches(13f, true, setOf("ss01"), context)).isFalse()
    assertThat(key.matches(13f, false, setOf(), context)).isFalse()
    assertThat(key.matches(13f, false, setOf("ss01"), null)).isFalse()
  }
}
