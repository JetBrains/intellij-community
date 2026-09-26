// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ui.laf

import com.intellij.ide.ui.readThemeBeanForTest
import com.intellij.ide.ui.resolveThemeColorsForTest
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.ui.Gray
import org.junit.jupiter.api.Test
import java.awt.Color

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

  @Test
  fun `named color chain of any length`() {
    // the chain is intentionally long - a short one may be resolved by chance,
    // as the order of the color map is not the order of declaration
    val warnings = ArrayList<String>()
    val colors = resolveThemeColorsForTest("""
      {
        "colors": {
          "toggle-off-disabled-bg": "control-bg-disabled",
          "control-bg-disabled": "dialog-bg",
          "dialog-bg": "layer-1-bg",
          "layer-1-bg": "gray-160",
          "gray-160": "gray-160-base",
          "gray-160-base": "#F7F8F9"
        }
      }
    """.trimIndent(), warn = { m, _ -> warnings.add(m) })

    assertThat(warnings).isEmpty()
    assertThat(colors).hasSize(6)
    assertThat(colors.values.map { it.rgb }.toSet()).isEqualTo(setOf(Color(0xF7F8F9).rgb))
  }

  @Test
  fun `not mapped named color`() {
    val warnings = ArrayList<String>()
    val colors = resolveThemeColorsForTest("""
      {
        "colors": {
          "a": "b"
        }
      }
    """.trimIndent(), warn = { m, _ -> warnings.add(m) })

    assertThat(warnings).containsExactly("Color b is not mapped for key a")
    assertThat(colors.get("a")).isEqualTo(Gray.TRANSPARENT)
  }

  @Test
  fun `not mapped named color in a chain`() {
    val warnings = ArrayList<String>()
    val colors = resolveThemeColorsForTest("""
      {
        "colors": {
          "a": "b",
          "b": "c"
        }
      }
    """.trimIndent(), warn = { m, _ -> warnings.add(m) })

    assertThat(warnings).containsExactlyInAnyOrder(
      "Color c is not mapped for key b",
      "Can't calculate color c for key 'a': c is not mapped",
    )
    assertThat(colors.get("a")).isNull()
    assertThat(colors.get("b")).isEqualTo(Gray.TRANSPARENT)
  }

  @Test
  fun `cyclic named color`() {
    val warnings = ArrayList<String>()
    val colors = resolveThemeColorsForTest("""
      {
        "colors": {
          "a": "b",
          "b": "c",
          "c": "a",
          "self": "self"
        }
      }
    """.trimIndent(), warn = { m, _ -> warnings.add(m) })

    assertThat(warnings).hasSize(4)
    assertThat(warnings).allMatch { it.contains("Can't ") }
    assertThat(colors).isEmpty()
  }
}
