/*
 * Copyright (C) 2022 The Android Open Source Project
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

import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

abstract class ComposeOverrideImplementsAnnotationsFilterTest : KotlinLightCodeInsightFixtureTestCase() {
  private val filter = ComposeOverrideImplementsAnnotationsFilter()

  fun testKotlinFileWithComposeDependency() {
    // Prepare
    myFixture.addFileToProject(
      "Composable.kt",
      """
        package androidx.compose.runtime
        
        annotation class Composable
      """.trimIndent()
    )
    val file = myFixture.addFileToProject(
      "MyFile.kt",
      """
        package test
        
        import androidx.compose.runtime.Composable
        
        interface Base {
          @Composable
          fun view()
        }
        
        class BaseImpl : Base
      """.trimIndent()
    )

    // Do
    val annotations = filter.getAnnotations(file)

    // Check
    assertEquals(listOf(COMPOSABLE_ANNOTATION_FQ_NAME.asString()), annotations.toList())
  }

  fun testKotlinFileWithoutComposeDependency() {
    // Prepare
    val file = myFixture.addFileToProject(
      "MyFile.kt",
      """
        package test
        
        import androidx.compose.runtime.Composable
        
        interface Base {
          @Composable
          fun view()
        }
        
        class BaseImpl : Base {}
      """.trimIndent()
    )

    // Do
    val annotations = filter.getAnnotations(file)

    // Check
    assertEquals(emptyList<String>(), annotations.toList())
  }

  fun testJavaFile() {
    // Prepare
    myFixture.addFileToProject(
      "Composable.kt",
      """
        package androidx.compose.runtime
        
        annotation class Composable
      """.trimIndent()
    )
    myFixture.addFileToProject(
      "Base.kt",
      """
        package test
        
        import androidx.compose.runtime.Composable
        
        interface Base {
          @Composable
          fun view()
        }
      """.trimIndent()
    )
    val file = myFixture.addFileToProject(
      "BaseImpl.java",
      """
        package test;
        
        import androidx.compose.runtime.Composable;
        
        class BaseImpl implements Base {}
      """.trimIndent()
    )

    // Do
    val annotations = filter.getAnnotations(file)

    // Check
    assertEquals(emptyList<String>(), annotations.toList())
  }
}
