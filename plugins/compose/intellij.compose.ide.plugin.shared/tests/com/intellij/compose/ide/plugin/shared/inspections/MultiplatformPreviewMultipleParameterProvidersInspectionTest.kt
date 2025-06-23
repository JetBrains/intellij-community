// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared.inspections

import com.intellij.compose.ide.plugin.shared.COMPOSABLE_ANNOTATION_FQ_NAME
import com.intellij.compose.ide.plugin.shared.JETPACK_PREVIEW_TOOLING_PACKAGE
import com.intellij.compose.ide.plugin.shared.MULTIPLATFORM_PREVIEW_TOOLING_PACKAGE
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test for [MultiplatformPreviewMultipleParameterProvidersInspection].
 */
@RunWith(Parameterized::class)
abstract class MultiplatformPreviewMultipleParameterProvidersInspectionTest(
  private val previewPackageName: String,
  private val parametersPackageName: String,
) : PreviewInspectionTest() {
  @Suppress("TestFunctionName")
  @Test
  fun test() = runPreviewInspectionTest(
    MultiplatformPreviewMultipleParameterProvidersInspection(),
    """
      import $previewPackageName.Preview
      import $parametersPackageName.PreviewParameter
      import $parametersPackageName.PreviewParameterProvider
      import ${COMPOSABLE_ANNOTATION_FQ_NAME.asString()}
      
      class IntProvider: PreviewParameterProvider<Int> {
          override val values: Sequence<Int> = sequenceOf(1, 2)
      }
      
      @Preview
      @Composable
      fun PreviewWithMultipleProviders(@PreviewParameter(IntProvider::class) a: Int,
                                       @PreviewParameter(IntProvider::class) b: Int) { // ERROR, only one PreviewParameter is supported
      }
      
      @Preview
      annotation class MyAnnotation
      
      @MyAnnotation
      @Composable
      fun MultiPreviewWithMultipleProviders(@PreviewParameter(IntProvider::class) a: Int,
                                            @PreviewParameter(IntProvider::class) b: Int) { // ERROR, only one PreviewParameter is supported
      }
      
      @MyAnnotation
      @Composable
      fun MixedPreviewProviders1(@androidx.compose.ui.tooling.preview.PreviewParameter(IntProvider::class) a: Int,
                                 @org.jetbrains.compose.ui.tooling.preview.PreviewParameter(IntProvider::class) b: Int) { // ERROR, only one PreviewParameter is supported
      }
      
      @MyAnnotation
      @Composable
      fun MixedPreviewProviders2(@org.jetbrains.compose.ui.tooling.preview.PreviewParameter(IntProvider::class) a: Int,
                                 @androidx.compose.ui.tooling.preview.PreviewParameter(IntProvider::class) b: Int) { // ERROR, only one PreviewParameter is supported
      }
    """.trimIndent(),
    """|12: Multiple @PreviewParameter are not allowed
                       |21: Multiple @PreviewParameter are not allowed
                       |27: Multiple @PreviewParameter are not allowed
                       |33: Multiple @PreviewParameter are not allowed
    """.trimMargin()
  )

  companion object {
    @Parameterized.Parameters(name = "{0} & {1}")
    @JvmStatic
    fun data(): Array<Array<*>> = arrayOf(
      arrayOf(JETPACK_PREVIEW_TOOLING_PACKAGE, JETPACK_PREVIEW_TOOLING_PACKAGE),
      arrayOf(JETPACK_PREVIEW_TOOLING_PACKAGE, MULTIPLATFORM_PREVIEW_TOOLING_PACKAGE),
      arrayOf(MULTIPLATFORM_PREVIEW_TOOLING_PACKAGE, JETPACK_PREVIEW_TOOLING_PACKAGE),
      arrayOf(MULTIPLATFORM_PREVIEW_TOOLING_PACKAGE, MULTIPLATFORM_PREVIEW_TOOLING_PACKAGE),
    )
  }
}
