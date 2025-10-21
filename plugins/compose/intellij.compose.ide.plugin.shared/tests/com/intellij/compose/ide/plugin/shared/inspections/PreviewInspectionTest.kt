// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared.inspections

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.compose.ide.plugin.shared.JETPACK_PREVIEW_TOOLING_PACKAGE
import com.intellij.compose.ide.plugin.shared.MULTIPLATFORM_PREVIEW_TOOLING_PACKAGE
import com.intellij.compose.ide.plugin.shared.util.enableComposeInTest
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.text.StringUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

abstract class PreviewInspectionTest : KotlinLightCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()

    myFixture.enableComposeInTest()
    myFixture.addFileToProject(
      "JetpackPreview.kt",
      //language=kotlin
      """
        package $JETPACK_PREVIEW_TOOLING_PACKAGE
            
        annotation class Preview(val name: String = "", val locale: String = "")
        annotation class PreviewParameter(val provider: KClass<out PreviewParameterProvider<*>>)
        interface PreviewParameterProvider<T> {
          val values: Sequence<T>
        }
      """.trimIndent(),
    )
    myFixture.addFileToProject(
      "MultiplatformPreview.kt",
      //language=kotlin
      """
        package $MULTIPLATFORM_PREVIEW_TOOLING_PACKAGE
            
        annotation class Preview
        annotation class PreviewParameter(val provider: KClass<out PreviewParameterProvider<*>>)
        interface PreviewParameterProvider<T> {
          val values: Sequence<T>
        }
      """.trimIndent(),
    )
  }

  internal fun runPreviewInspectionTest(
    inspection: BasePreviewAnnotationInspection,
    @Language("kotlin") fileContent: String,
    expectedOutput: String,
  ) {
    myFixture.enableInspections(
      inspection as InspectionProfileEntry
    )

    myFixture.configureByText("Test.kt", fileContent)
    val inspections =
      myFixture
        .doHighlighting(HighlightSeverity.ERROR)
        // Filter out UNRESOLVED_REFERENCE caused by the standard library not being available.
        // `sequence` and `sequenceOf` are not available. We can safely ignore them.
        .filter { !it.description.contains("[UNRESOLVED_REFERENCE]") }
        .sortedByDescending { -it.startOffset }
        .joinToString("\n") { descriptionWithLineNumber(it) }

    assertEquals(expectedOutput, inspections, )
  }

  private fun descriptionWithLineNumber(info: HighlightInfo) =
    ReadAction.compute<String, Throwable> {
      "${StringUtil.offsetToLineNumber(info.highlighter!!.document.text, info.startOffset)}: ${info.description}"
    }
}
