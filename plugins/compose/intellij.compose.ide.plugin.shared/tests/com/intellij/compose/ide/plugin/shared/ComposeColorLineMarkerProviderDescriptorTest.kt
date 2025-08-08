/*
 * Copyright (C) 2019 The Android Open Source Project
 * Modified 2025 by JetBrains s.r.o.
 * Copyright (C) 2025 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.shared

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.LineMarkersPass
import com.intellij.compose.ide.plugin.shared.util.moveCaret
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.writeText
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.utils.vfs.createFile
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradleImportingTestCase
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import java.awt.Color
import kotlin.reflect.KClass
import kotlin.test.assertEquals as kAssertEquals
import kotlin.test.assertNotNull as kAssertNotNull

private const val TARGET_GRADLE_VERSION = "8.9"

@TestRoot("../../../community/plugins/compose/intellij.compose.ide.plugin.shared/testData")
@TestMetadata("")
abstract class ComposeColorLineMarkerProviderDescriptorTest : KotlinGradleImportingTestCase() {

  abstract override val pluginMode: KotlinPluginMode

  private var _codeInsightTestFixture: CodeInsightTestFixture? = null

  private val codeInsightTestFixture: CodeInsightTestFixture
    get() = kAssertNotNull(_codeInsightTestFixture, "_codeInsightTestFixture was not initialized")

  @Parameter(1)
  lateinit var sourceSetName: String

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeColorLineMarker")
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

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeColorLineMarker")
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

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeColorLineMarker")
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

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeColorLineMarker")
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

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeColorLineMarker")
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

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeColorLineMarker")
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

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeColorLineMarker")
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

  @TargetVersions(TARGET_GRADLE_VERSION)
  @Test
  @TestMetadata("ComposeColorLineMarker")
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
  ) {
    importProjectFromTestData()

    val colorFile = runWriteActionAndWait {
      myProjectRoot.createFile("composeApp/src/$sourceSetName/kotlin/org/example/project/A.kt").apply {
        writeText(code.trimIndent())
      }
    }

    val countExpectedProviders = LineMarkersPass.getMarkerProviders(KotlinLanguage.INSTANCE, myProject).count {
      expectedComposeColorLineMarkerProviderDescriptorClass.isInstance(it)
    }
    kAssertEquals(1, countExpectedProviders, "Expected single ${expectedComposeColorLineMarkerProviderDescriptorClass.java.name} provider")

    codeInsightTestFixture.configureFromExistingVirtualFile(colorFile)

    checkGutterIconInfos(expectedColorIcons)

    setColors.forEach { (window, newColor) ->
      setNewColor(window, newColor)
    }

    expectedSetColorCode?.let {
      kAssertEquals(it.trimIndent(), codeInsightTestFixture.editor.document.text)
    }
  }

  private fun checkGutterIconInfos(expectedColorIcons: List<Color>) {
    codeInsightTestFixture.doHighlighting()

    val highlightInfos = runReadAction {
      DaemonCodeAnalyzerImpl.getLineMarkers(codeInsightTestFixture.editor.document, myProject)
        .filter { lineMarkerInfo -> lineMarkerInfo.navigationHandler is ColorIconRenderer }
        .sortedBy { it.startOffset }
    }

    assertEquals(expectedColorIcons.size, highlightInfos.size)
    highlightInfos.forEach {
      kAssertNotNull(it.icon)
      kAssertNotNull(it.navigationHandler)
    }

    assertEquals(expectedColorIcons, highlightInfos.map { (it.navigationHandler as ColorIconRenderer).color })
  }

  private fun setNewColor(window: String, newColor: Color) {
    val element = runInEdtAndGet { codeInsightTestFixture.moveCaret(window) }

    codeInsightTestFixture.doHighlighting()
    val highlightInfo = runReadAction {
      DaemonCodeAnalyzerImpl.getLineMarkers(codeInsightTestFixture.editor.document, myProject)
        .single { lineMarkerInfo ->
          lineMarkerInfo.navigationHandler is ColorIconRenderer && lineMarkerInfo.element == element
        }
    }

    runInEdtAndWait {
      val setColorTask =
        (highlightInfo.navigationHandler as ColorIconRenderer).getSetColorTask()
        ?: return@runInEdtAndWait
      WriteCommandAction.runWriteCommandAction(
        myProject,
        ComposeIdeBundle.message("compose.color.picker.action.name"),
        null,
        { setColorTask.invoke(newColor) },
      )
    }
  }

  protected abstract val expectedComposeColorLineMarkerProviderDescriptorClass: KClass<out ComposeColorLineMarkerProviderDescriptor>

  override fun setUpFixtures() {
    myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName()).fixture
    _codeInsightTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myTestFixture)
    codeInsightTestFixture.setUp()
  }

  override fun tearDownFixtures() {
    runAll(
      { _codeInsightTestFixture?.tearDown() },
      { _codeInsightTestFixture = null },
      { resetTestFixture() },
    )
  }

  companion object {

    @JvmStatic
    @Suppress("ACCIDENTAL_OVERRIDE")
    @Parameters(name = "source set {1} with Gradle {0}")
    fun data() = listOf(
      "commonMain",
      "desktopMain",
      "wasmJsMain",
    ).map { arrayOf(TARGET_GRADLE_VERSION, it) }
  }
}