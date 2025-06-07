// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.laf

import com.intellij.ide.ui.customization.UIThemeCustomizer
import com.intellij.ide.ui.readThemeBeanForTest
import com.intellij.openapi.Disposable
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import org.junit.jupiter.api.Test
import java.awt.Color

@TestApplication
class UIThemeCustomizerTests {

  @Test
  fun shouldDoNothingIfCustomizerIsEmpty() {
    val iconPathMap = mutableMapOf<String, String>()
    val awtColorMap = mutableMapOf<String, Color>()
    val namedColorMap = mutableMapOf<String, String>()
    readThemeBeanForTest(
      """
      {
        "author": "No one",
        "name": "Theme Name",
        "colors": {
          "black": "#000000",
          "Grey": "#1C1C1C",
          "foreground": "black"
        },
        "icons": {
          "/test/icon.svg": "/test/new-icon.svg",
          "ColorPalette": {
            "Checkbox.Background.Default": "Gray14"
          }
        }
      }
    """,
      warn = { m, e -> throw RuntimeException(m, e) },
      iconConsumer = { key, value ->
        if (value is String) {
          iconPathMap[key] = value
        }
        else {
          iconPathMap[key] = "ANY"
        }
      },
      awtColorConsumer = { key, value ->
        awtColorMap[key] = value
      },
      namedColorConsumer = { key, value ->
        namedColorMap[key] = value
      }
    )
    assertThat(iconPathMap.size).isEqualTo(2)
    assertThat(iconPathMap["/test/icon.svg"]).isEqualTo("/test/new-icon.svg")
    assertThat(iconPathMap["ColorPalette"]).isNotNull
    assertThat(awtColorMap.size).isEqualTo(2)
    assertThat(awtColorMap["black"]).isEqualTo(Color(0x000000))
    assertThat(awtColorMap["Grey"]).isEqualTo(Color(0x1c1c1c))
    assertThat(namedColorMap.size).isEqualTo(1)
    assertThat(namedColorMap["foreground"]).isEqualTo("black")
  }

  @Test
  fun shouldCorrectPatchIcons(@TestDisposable disposable: Disposable) {
    application.replaceService(UIThemeCustomizer::class.java, object : UIThemeCustomizer {
      override fun createColorCustomizer(themeName: String): Map<String, Color> {
        return if (themeName == "Theme Name") {
          mapOf("black" to Color(0x000001), "white" to Color(0xffffff))
        }
        else {
          emptyMap()
        }
      }

      override fun createNamedColorCustomizer(themeName: String): Map<String, String> {
        return if (themeName == "Theme Name") {
          mapOf("foreground" to "white", "background" to "black")
        }
        else {
          emptyMap()
        }
      }

      override fun createIconCustomizer(themeName: String): Map<String, String> {
        return if (themeName == "Theme Name") {
          mapOf("/test/icon.svg" to "/test/icon-patched.svg", "/added-icon.svg" to "/new-added-icon.svg")
        }
        else {
          emptyMap()
        }
      }

      override fun createEditorThemeCustomizer(themeName: String): Map<String, String> {
        return emptyMap()
      }
    }, disposable)
    val iconPathMap = mutableMapOf<String, String>()
    val awtColorMap = mutableMapOf<String, Color>()
    val namedColorMap = mutableMapOf<String, String>()
    readThemeBeanForTest(
      """
      {
        "author": "No one",
        "name": "Theme Name",
        "colors": {
          "black": "#000000",
          "Grey": "#1C1C1C",
          "foreground": "black"
        },
        "icons": {
          "/test/icon.svg": "/test/new-icon.svg",
          "ColorPalette": {
            "Checkbox.Background.Default": "Gray14"
          }
        }
      }
    """,
      warn = { m, e -> throw RuntimeException(m, e) },
      iconConsumer = { key, value ->
        if (value is String) {
          iconPathMap[key] = value
        }
        else {
          iconPathMap[key] = "ANY"
        }
      },
      awtColorConsumer = { key, value ->
        awtColorMap[key] = value
      },
      namedColorConsumer = { key, value ->
        namedColorMap[key] = value
      }
    )
    assertThat(iconPathMap.size).isEqualTo(3)
    assertThat(iconPathMap["/test/icon.svg"]).isEqualTo("/test/icon-patched.svg")
    assertThat(iconPathMap["/added-icon.svg"]).isEqualTo("/new-added-icon.svg")
    assertThat(iconPathMap["ColorPalette"]).isNotNull
    assertThat(awtColorMap.size).isEqualTo(3)
    assertThat(awtColorMap["black"]).isEqualTo(Color(0x000001))
    assertThat(awtColorMap["white"]).isEqualTo(Color(0xffffff))
    assertThat(awtColorMap["Grey"]).isEqualTo(Color(0x1c1c1c))
    assertThat(namedColorMap.size).isEqualTo(2)
    assertThat(namedColorMap["foreground"]).isEqualTo("white")
    assertThat(namedColorMap["background"]).isEqualTo("black")
  }
}