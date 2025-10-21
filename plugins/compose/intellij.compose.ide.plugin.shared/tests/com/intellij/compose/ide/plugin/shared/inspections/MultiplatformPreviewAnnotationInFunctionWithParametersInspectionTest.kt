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
package com.intellij.compose.ide.plugin.shared.inspections

import com.intellij.compose.ide.plugin.shared.COMPOSABLE_ANNOTATION_FQ_NAME
import com.intellij.compose.ide.plugin.shared.JETPACK_PREVIEW_TOOLING_PACKAGE
import com.intellij.compose.ide.plugin.shared.MULTIPLATFORM_PREVIEW_TOOLING_PACKAGE
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
abstract class MultiplatformPreviewAnnotationInFunctionWithParametersInspectionTest(
  private val previewPackageName: String,
  private val parametersPackageName: String,
) : PreviewInspectionTest() {
  @Suppress("TestFunctionName")
  @Test
  fun test() = runPreviewInspectionTest(
    MultiplatformPreviewAnnotationInFunctionWithParametersInspection(),
    """
      import $previewPackageName.Preview
      import $parametersPackageName.PreviewParameter
      import $parametersPackageName.PreviewParameterProvider
      import ${COMPOSABLE_ANNOTATION_FQ_NAME.asString()}
      
      @Preview
      @Composable
      fun Preview1(a: Int) { // ERROR, no defaults
      }
      
      @Preview
      @Composable
      fun Preview2(b: String = "hello") {
      }
      
      @Preview
      @Composable
      fun Preview3(a: Int, b: String = "hello") { // ERROR, the first parameter has no default
      }
      
      class IntProvider: PreviewParameterProvider<Int> {
          override val values: Sequence<Int> = sequenceOf(1, 2)
      }
      
      @Preview
      @Composable
      fun PreviewWithProvider(@PreviewParameter(IntProvider::class) a: Int) {
      }
      
      @Preview
      @Composable
      fun PreviewWithProviderAndDefault(@PreviewParameter(IntProvider::class) a: Int, b: String = "hello") {
      }
      
      @Preview
      @Composable
      fun PreviewWithProviderAndNoDefault(@PreviewParameter(IntProvider::class) a: Int, b: String) { // ERROR, no default in second parameter
      }
      
      annotation class MyEmptyAnnotation
      
      @MyEmptyAnnotation
      @Composable
      fun NotMultiPreview(a: Int){ // No error, because MyEmptyAnnotation is not a MultiPreview, as it doesn't provide Previews
      }
      
      @Preview
      annotation class MyAnnotation
      
      @MyAnnotation
      @Composable
      fun MultiPreviewWithProviderAndDefault(@PreviewParameter(IntProvider::class) a: Int, b: String = "hello") {
      }
      
      @MyAnnotation
      @Composable
      fun MultiPreviewWithProviderAndNoDefault(@PreviewParameter(IntProvider::class) a: Int, b: String) { // ERROR, no default in second parameter
      }
    """.trimIndent(),
    """5: Composable functions with non-default parameters are not supported in Preview unless they are annotated with @PreviewParameter
        |15: Composable functions with non-default parameters are not supported in Preview unless they are annotated with @PreviewParameter
        |34: Composable functions with non-default parameters are not supported in Preview unless they are annotated with @PreviewParameter
        |54: Composable functions with non-default parameters are not supported in Preview unless they are annotated with @PreviewParameter"""
      .trimMargin()
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
