// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FontFamiliesTest {
  @Test
  fun `equal names give equal values`() {
    val first = FontFamilies.of("JetBrains Mono", "Noto Serif")
    val second = FontFamilies.of(listOf("JetBrains Mono", "Noto Serif"))

    assertEquals(first, second)
    assertEquals(first.hashCode(), second.hashCode())
    assertNotEquals(first, FontFamilies.of("Noto Serif", "JetBrains Mono"))
  }

  @Test
  fun `the names are an unmodifiable copy`() {
    val names = mutableListOf("JetBrains Mono")
    val fontFamilies = FontFamilies.of(names)

    names.add("Noto Serif")

    assertEquals(listOf("JetBrains Mono"), fontFamilies.names())
    assertThrows<UnsupportedOperationException> {
      (fontFamilies.names() as MutableList<String>).add("Noto Serif")
    }
  }

  @Test
  fun `invalid names are rejected`() {
    assertThrows<IllegalArgumentException> { FontFamilies.of(emptyList()) }
    assertThrows<IllegalArgumentException> { FontFamilies.of(" ") }
    assertThrows<IllegalArgumentException> { FontFamilies.of(" JetBrains Mono") }
    assertThrows<IllegalArgumentException> { FontFamilies.of("JetBrains Mono,Noto Serif") }
    assertThrows<IllegalArgumentException> { FontFamilies.of("JetBrains Mono", "JetBrains Mono") }
  }

  @Test
  fun `a null name from Java is rejected`() {
    @Suppress("UNCHECKED_CAST")
    val names = listOf("JetBrains Mono", null) as List<String>

    assertThrows<IllegalArgumentException> { FontFamilies.of(names) }
  }
}
