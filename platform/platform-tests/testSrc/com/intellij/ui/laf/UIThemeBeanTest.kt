// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ui.laf

import com.intellij.ide.ui.readThemeBeanForTest
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.jupiter.api.Test

class UIThemeBeanTest {
  @Test
  fun readName() {
    val bean = readThemeBeanForTest("""
      {
        "author": "No one",
        "icons": {
          "ColorPalette": {
          }
        },
        "name": "Theme Name"
      }
    """.trimIndent(), warn = { m, e -> throw RuntimeException(m, e) })

    assertThat(bean.get("author")).isEqualTo("No one")
    assertThat(bean.get("name")).isEqualTo("Theme Name")
  }

  @Test
  fun `null as string`() {
    var error = ""
    readThemeBeanForTest("""
      {
        "author": "No one",
        "ui": {
          "Editor": {
            "tabInsets": "null"
          }
        },
        "name": "Theme Name"
      }
    """.trimIndent(), warn = { m, e ->
      error = m
    })

    assertThat(error).isEqualTo("Cannot parse null for Editor.tabInsets")
  }
}