package com.intellij.compose.ide.plugin

import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class ComposeOverrideImplementsAnnotationsFilterTest : KotlinLightCodeInsightFixtureTestCase() {
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
