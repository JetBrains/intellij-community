// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editor

import com.intellij.openapi.editor.colors.impl.loadBundledSchemes
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class BundledEditorColorSchemeTest {
  @Test
  fun `id equals to name`() {
    val schemes = loadBundledSchemes(additionalTextAttributes = HashMap(), checkId = true).toList()
    assertThat(schemes.isNotEmpty())
    for (scheme in schemes) {
      assertThat(scheme.schemeKey).isNotEmpty()
      assertThat(scheme.schemeKey).doesNotEndWith(".xml")
    }
  }
}