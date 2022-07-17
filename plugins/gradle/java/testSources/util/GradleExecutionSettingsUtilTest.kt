// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.JavaPsiTestCase
import org.junit.Test

class GradleExecutionSettingsUtilTest : JavaPsiTestCase() {

  fun `test filter generation by name`() {
    assertEquals("""--tests *""", createTestFilterFromPackage(""))
    assertEquals("""--tests "org.jetbrains.test.*"""", createTestFilterFromPackage("org.jetbrains.test"))
    assertEquals("""--tests "org.jetbrains.te*st.*"""", createTestFilterFromPackage("org.jetbrains.te\"st"))
    assertEquals("""--tests "org.jetbrains.te\st.*"""", createTestFilterFromPackage("org.jetbrains.te\\st"))
    assertEquals("""--tests "My favorite test case"""", createTestFilterFromClass("My favorite test case"))
    assertEquals("""--tests "It isn't a favorite * test case"""", createTestFilterFromClass("It isn't a favorite \" test case"))
    assertEquals("""--tests "Test case.it is my favorite test"""", createTestFilterFromMethod("Test case", "it is my favorite test"))
    assertEquals("""--tests "Test.it isn't a favorite * test"""", createTestFilterFromMethod("Test", "it isn't a favorite . test"))
  }

  @Test
  fun `test filter generation by groovy method`() {
    val psiFile = createGroovyPsiFile(
      "Test",
      "test",
      "'test'",
      "'tes\\\'t'",
      "'tes\\\\\\\'t'",
      "'tes\\\\t'",
      "'tes\\\\\\\\t'",
      "'t\\\\es\\\\t'"
    )
    val actualFilters = runReadActionAndWait {
      val aClass = psiFile.findChildByType<PsiClass>()
      val methods = aClass.findChildByElementType("CLASS_BODY")
        .findChildrenByType<PsiMethod>()
      methods.map { createTestFilterFrom(aClass, it) }
    }
    val expectedFilters = listOf(
      """--tests "Test.test"""",
      """--tests "Test.test"""",
      """--tests "Test.tes't"""",
      """--tests "Test.tes\'t"""",
      """--tests "Test.tes\t"""",
      """--tests "Test.tes\\t"""",
      """--tests "Test.t\es\t""""
    )
    for ((expected, actual) in expectedFilters.zip(actualFilters)) {
      assertEquals(expected, actual)
    }
  }

  @Test
  fun `test filter generation by groovy class`() {
    val psiFile = createGroovyPsiFile("Test")
    val actualFilter = runReadActionAndWait {
      val aClass = psiFile.findChildByType<PsiClass>()
      createTestFilterFrom(aClass)
    }
    assertEquals("""--tests "Test"""", actualFilter)
  }

  @Test
  fun `test filter generation by java method`() {
    val psiFile = createJavaPsiFile(
      "Test",
      "test",
      "test2",
      "super_test",
      "super${'$'}test"
    )
    val actualFilters = runReadActionAndWait {
      val aClass = psiFile.findChildByType<PsiClass>()
      val methods = aClass.findChildrenByType<PsiMethod>()
      methods.map { createTestFilterFrom(aClass, it) }
    }
    val expectedFilters = listOf(
      """--tests "Test.test"""",
      """--tests "Test.test2"""",
      """--tests "Test.super_test"""",
      """--tests "Test.super${'$'}test""""
    )
    for ((expected, actual) in expectedFilters.zip(actualFilters)) {
      assertEquals(expected, actual)
    }
  }

  @Test
  fun `test filter generation by java class`() {
    val psiFile = createJavaPsiFile("Te${'$'}${'$'}${'$'}st")
    val actualFilter = runReadActionAndWait {
      val aClass = psiFile.findChildByType<PsiClass>()
      createTestFilterFrom(aClass)
    }
    assertEquals("""--tests "Te${'$'}${'$'}${'$'}st"""", actualFilter)
  }

  private fun createJavaFileContent(className: String, vararg methodNames: String): String {
    val methods = methodNames.map {
      """
        |  @Test
        |  void $it() {
        |    fail()
        |  }
      """.trim()
    }
    val classBody = methods.joinToString("\n")
    return """
      |import static org.junit.Assert.fail
      |import org.junit.Test
      |
      |class $className {
        $classBody
      |}
    """.trim().replaceIndentByMargin()
  }

  private fun createGroovyPsiFile(className: String, vararg methodNames: String): PsiFile {
    val content = createJavaFileContent(className, *methodNames)
    return createFile("$className.groovy", content)
  }

  private fun createJavaPsiFile(className: String, vararg methodNames: String): PsiFile {
    val content = createJavaFileContent(className, *methodNames)
    return createFile("$className.java", content)
  }
}