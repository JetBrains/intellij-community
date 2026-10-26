// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.codeInsight

import com.intellij.codeInsight.daemon.impl.LineMarkersPass
import com.intellij.codeInsight.daemon.impl.LineMarkersPass.getDisplayedLineMarkers
import com.intellij.compose.ide.plugin.codeInsight.lineMarkers.ColorIconRenderer
import com.intellij.compose.ide.plugin.codeInsight.lineMarkers.ComposeColorLineMarkerProviderDescriptor
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.plugins.gradle.testFramework.fixtures.FileTestFixture
import org.junit.jupiter.api.Test
import java.awt.Color
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val COLOR_LINE_MARKER_PROJECT_NAME: String = "ComposeColorLineMarker"

@ComposeCodeInsightAllSourceSets
internal class ComposeColorLineMarkerProviderDescriptorTest : ComposeCodeInsightTestCase() {

  override val testDataProjectRoot: Path
    get() = super.testDataProjectRoot.resolve(COLOR_LINE_MARKER_PROJECT_NAME)

  override fun FileTestFixture.Builder.configureProject() {
    withAndroidSdk()
  }

  @Test
  fun testColorLong() = doTest(
    code = """
      package org.example.project
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xFFC20029)
        fun () {
          val primary = Color(0xA84A8A7B)
          val secondary = Color(0xFF4A8A7B)
          val primaryVariant = Color(color = 0xFF57AD28)
          val secondaryVariant = Color(color = 0x8057AD28)
        }
      }
    """,
    expectedColorIcons = listOf(
      Color(194, 0, 41, 255),
      Color(74, 138, 123, 168),
      Color(74, 138, 123, 255),
      Color(87, 173, 40, 255),
      Color(87, 173, 40, 128),
    ),
    setColors = listOf(
      "Co|lor(0xFF4A8A7B)" to Color(0xFFAABBCC.toInt()),
      "Co|lor(color = 0xFF57AD28)" to Color(0xFFAABBCC.toInt()),
    ),
    expectedSetColorCode = """
      package org.example.project
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xFFC20029)
        fun () {
          val primary = Color(0xA84A8A7B)
          val secondary = Color(0xFFAABBCC)
          val primaryVariant = Color(color = 0xFFAABBCC)
          val secondaryVariant = Color(color = 0x8057AD28)
        }
      }
    """
  )

  @Test
  fun testColorWithLeadingZero() = doTest(
    code = """
      package org.example.project
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xFFFF0000)
      }
    """,
    expectedColorIcons = listOf(
      Color(255, 0, 0, 255),
    ),
    setColors = listOf(
      "Co|lor(0xFFFF0000)" to Color(0x0DFF0000, true),
    ),
    expectedSetColorCode = """
      package org.example.project
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0x0DFF0000)
      }
    """
  )

  @Test
  fun testColorInt() = doTest(
    code = """
      package org.example.project
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xC20029)
        fun () {
          val primary = Color(0x4A8A7B)
          val secondary = Color(0x804A8A7B)
          val primaryVariant = Color(color = 0x57AD28)
          val secondaryVariant = Color(color = 0x4057AD28)
        }
      }
    """,
    expectedColorIcons = listOf(
      Color(194, 0, 41, 0),
      Color(74, 138, 123, 0),
      Color(74, 138, 123, 128),
      Color(87, 173, 40, 0),
      Color(87, 173, 40, 64),
    ),
    setColors = listOf(
      "Co|lor(0x4A8A7B)" to Color(0xFFAABBCC.toInt()),
      "Co|lor(color = 0x57AD28)" to Color(0xFFAABBCC.toInt())
    ),
    expectedSetColorCode = """
      package org.example.project
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xC20029)
        fun () {
          val primary = Color(0xFFAABBCC)
          val secondary = Color(0x804A8A7B)
          val primaryVariant = Color(color = 0xFFAABBCC)
          val secondaryVariant = Color(color = 0x4057AD28)
        }
      }
    """
  )

  @Test
  fun testColorInt_X3() = doTest(
    code = """
      package org.example.project
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xC2, 0x00, 0x29)
        fun () {
          val primary = Color(0x4A, 0x8A, 0x7B)
          val secondary = Color(170, 187, 204)
          val primaryVariant = Color(red = 0x57, green = 0xAD, blue = 0x28)
          val secondaryVariant = Color(green = 200, red = 180, blue = 120)
        }
      }
    """,
    expectedColorIcons = listOf(
      Color(194, 0, 41),
      Color(74, 138, 123),
      Color(170, 187, 204),
      Color(87, 173, 40),
      Color(180, 200, 120),
    ),
    setColors = listOf(
      "Co|lor(0x4A, 0x8A, 0x7B)" to Color(0xFFAABBCC.toInt()),
      "Co|lor(170, 187, 204)" to Color(0xFF406080.toInt()),
      "Co|lor(red = 0x57, green = 0xAD, blue = 0x28)" to Color(0xFFAABBCC.toInt()),
      "Co|lor(green = 200, red = 180, blue = 120)" to Color(0x80112233.toInt()),
    ),
    expectedSetColorCode = """
      package org.example.project
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xC2, 0x00, 0x29)
        fun () {
          val primary = Color(0xAA, 0xBB, 0xCC, 0xFF)
          val secondary = Color(64, 96, 128, 255)
          val primaryVariant = Color(red = 0xAA, green = 0xBB, blue = 0xCC, alpha = 0xFF)
          val secondaryVariant = Color(red = 17, green = 34, blue = 51, alpha = 255)
        }
      }
    """
  )

  @Test
  fun testColorInt_X4() = doTest(
    code = """
      package org.example.project
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xC2, 0x00, 0x29, 0xFF)
        fun () {
          val primary = Color(0x4A, 0x8A, 0x7B, 0xFF)
          val secondary = Color(170, 187, 204, 255)
          val primaryVariant = Color(red = 0x57, green = 0xAD, blue = 0x28, alpha = 0xFF)
          val secondaryVariant = Color(green = 120, red = 64, alpha = 255, blue = 192)
        }
      }
    """,
    expectedColorIcons = listOf(
      Color(194, 0, 41),
      Color(74, 138, 123),
      Color(170, 187, 204),
      Color(87, 173, 40),
      Color(64, 120, 192),
    ),
    setColors = listOf(
      "Co|lor(0x4A, 0x8A, 0x7B, 0xFF)" to Color(0xFFAABBCC.toInt()),
      "Co|lor(green = 120, red = 64, alpha = 255, blue = 192)" to Color(0xFFAABBCC.toInt())
    ),
    expectedSetColorCode = """
      package org.example.project
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xC2, 0x00, 0x29, 0xFF)
        fun () {
          val primary = Color(0xAA, 0xBB, 0xCC, 0xFF)
          val secondary = Color(170, 187, 204, 255)
          val primaryVariant = Color(red = 0x57, green = 0xAD, blue = 0x28, alpha = 0xFF)
          val secondaryVariant = Color(red = 170, green = 187, blue = 204, alpha = 255)
        }
      }
    """
  )

  @Test
  fun testColorFloat_X3() = doTest(
    code = """
      package org.example.project
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0.14f, 0.0f, 0.16f)
        fun () {
          val primary = Color(0.3f, 0.54f, 0.48f)
          val primary = Color(0.3f, 0.54f, 0.48f)
          val primaryVariant = Color(red = 0.34f, green = 0.68f, blue = 0.15f)
          val primaryVariant = Color(green = 0.68f, red = 0.34f, blue = 0.15f)
        }
      }
    """,
    expectedColorIcons = listOf(
      Color(36, 0, 41),
      Color(77, 138, 122),
      Color(77, 138, 122),
      Color(87, 173, 38),
      Color(87, 173, 38),
    ),
    setColors = listOf(
      "Co|lor(0.3f, 0.54f, 0.48f)" to Color(0xFFAABBCC.toInt()),
      "Co|lor(green = 0.68f, red = 0.34f, blue = 0.15f)" to Color(0xFFAABBCC.toInt())
    ),
    expectedSetColorCode = """
      package org.example.project
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0.14f, 0.0f, 0.16f)
        fun () {
          val primary = Color(0.667f, 0.733f, 0.8f, 1.0f)
          val primary = Color(0.3f, 0.54f, 0.48f)
          val primaryVariant = Color(red = 0.34f, green = 0.68f, blue = 0.15f)
          val primaryVariant = Color(red = 0.667f, green = 0.733f, blue = 0.8f, alpha = 1.0f)
        }
      }
    """
  )

  @Test
  fun testColorFloat_X4() = doTest(
    code = """
      package org.example.project
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0.194f, 0f, 0.41f, 0.5f)
        fun () {
          val primary = Color(0.74f, 0.138f, 0.3f, 0.845f)
          val primary = Color(0.74f, 0.138f, 0.3f, 0.845f)
          val primaryVariant = Color(red = 0.87f, green = 0.173f, blue = 0.4f, alpha = 0.25f)
          val primaryVariant = Color(alpha = 0.25f, green = 0.173f, blue = 0.4f, red = 0.87f)
        }
      }
    """,
    expectedColorIcons = listOf(
      Color(49, 0, 105, 128),
      Color(189, 35, 77, 215),
      Color(189, 35, 77, 215),
      Color(222, 44, 102, 64),
      Color(222, 44, 102, 64),
    ),
    setColors = listOf(
      "Co|lor(0.74f, 0.138f, 0.3f, 0.845f)" to Color(0xFFAABBCC.toInt()),
      "Co|lor(alpha = 0.25f, green = 0.173f, blue = 0.4f, red = 0.87f)" to Color(0xFFAABBCC.toInt())
    ),
    expectedSetColorCode = """
      package org.example.project
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0.194f, 0f, 0.41f, 0.5f)
        fun () {
          val primary = Color(0.667f, 0.733f, 0.8f, 1.0f)
          val primary = Color(0.74f, 0.138f, 0.3f, 0.845f)
          val primaryVariant = Color(red = 0.87f, green = 0.173f, blue = 0.4f, alpha = 0.25f)
          val primaryVariant = Color(red = 0.667f, green = 0.733f, blue = 0.8f, alpha = 1.0f)
        }
      }
    """
  )

  @Test
  fun testColorFloat_X4_ColorSpace() {
    // Note: We don't offer neither color preview nor picker for Color(Float, Float, Float, Float,
    // ColorSpace) function.
    doTest(
      code = """
        package org.example.project
        import androidx.compose.ui.graphics.Color
        import androidx.compose.ui.graphics.colorspace.ColorSpaces
        class A {
          val other = Color(0.194f, 0f, 0.41f, 0.5f, ColorSpaces.LinearSrgb)
          fun () {
            val primary = Color(0.74f, 0.138f, 0.3f, 0.845f, ColorSpaces.LinearSrgb)
            val primary = Color(0f, 0f, 0f, 0f, ColorSpaces.LinearSrgb)
            val primaryVariant = Color(red = 0.87f, green = 0.173f, blue = 0.4f, alpha = 0.25f, colorSpace = ColorSpaces.LinearSrgb)
            val primaryVariant = Color(red = 1.0f, green = 1.0f, blue = 1.0f, alpha = 1.0f, colorSpace = ColorSpaces.LinearSrgb)
          }
        }
      """,
      expectedColorIcons = listOf(),
    )
  }

  private fun doTest(
    @Language("kotlin") code: String,
    expectedColorIcons: List<Color>,
    setColors: List<Pair<String, Color>> = emptyList(),
    @Language("kotlin") expectedSetColorCode: String? = null,
  ) = testComposeProject {
    try {
      val colorFilePath = "composeApp/src/$sourceSetName/kotlin/org/example/project/A.kt"
      writeText(colorFilePath, code.trimIndent())
      val countExpectedProviders = LineMarkersPass.getMarkerProviders(KotlinLanguage.INSTANCE, project).count {
        it is ComposeColorLineMarkerProviderDescriptor
      }
      assertEquals(1, countExpectedProviders, "Expected single ${ComposeColorLineMarkerProviderDescriptor::class.java.name} provider")

      codeInsightFixture.configureFromExistingVirtualFile(canonicalProjectFile(colorFilePath))

      checkGutterIconInfos(expectedColorIcons)

      setColors.forEach { (window, newColor) ->
        setNewColor(window, newColor)
      }

      expectedSetColorCode?.let {
        assertEquals(it.trimIndent(), codeInsightFixture.editor.document.text)
      }
    }
    finally {
      revertUnsavedDocuments()
    }
  }

  private fun checkGutterIconInfos(expectedColorIcons: List<Color>) {
    codeInsightFixture.doHighlighting()

    val highlightInfos = runReadActionBlocking {
      getDisplayedLineMarkers(codeInsightFixture.editor.document, project)
        .filter { lineMarkerInfo -> lineMarkerInfo.navigationHandler is ColorIconRenderer }
        .sortedBy { it.startOffset }
    }

    assertEquals(expectedColorIcons.size, highlightInfos.size)
    highlightInfos.forEach {
      assertNotNull(it.icon)
      assertNotNull(it.navigationHandler)
    }

    assertEquals(expectedColorIcons, highlightInfos.map { (it.navigationHandler as ColorIconRenderer).color })
  }

  private fun setNewColor(window: String, newColor: Color) {
    val element = runInEdtAndGet { codeInsightFixture.moveCaret(window) }

    codeInsightFixture.doHighlighting()
    val highlightInfo = runReadActionBlocking {
      getDisplayedLineMarkers(codeInsightFixture.editor.document, project)
        .single { lineMarkerInfo ->
          lineMarkerInfo.navigationHandler is ColorIconRenderer && lineMarkerInfo.element == element
        }
    }

    runInEdtAndWait {
      val setColorTask =
        (highlightInfo.navigationHandler as ColorIconRenderer).getSetColorTask()
        ?: return@runInEdtAndWait
      WriteCommandAction.runWriteCommandAction(
        project,
        ComposeIdeBundle.message("compose.color.picker.action.name"),
        null,
        { setColorTask.invoke(newColor) },
      )
    }
  }
}
