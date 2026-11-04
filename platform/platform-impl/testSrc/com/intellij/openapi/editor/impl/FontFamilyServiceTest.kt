// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Locale

@TestApplication
internal class FontFamilyServiceTest {
  @Test
  fun `isAvailable accepts each available family and no other name`() {
    val families = FontFamilyService.getAvailableFamilies()
    assertThat(families).isNotEmpty()
    for (family in families) {
      assertThat(FontFamilyService.isAvailable(family)).describedAs(family).isTrue()
    }
    assertThat(FontFamilyService.isAvailable("No Such Font Family")).isFalse()
  }

  @Test
  fun `isAvailable compares the names exactly`() {
    val families = FontFamilyService.getAvailableFamilies()
    val family = families.first { it.uppercase(Locale.ENGLISH) != it && it.uppercase(Locale.ENGLISH) !in families }
    assertThat(FontFamilyService.isAvailable(family.uppercase(Locale.ENGLISH))).isFalse()
  }
}
