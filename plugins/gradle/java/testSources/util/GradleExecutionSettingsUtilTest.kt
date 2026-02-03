// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.execution.PsiLocation
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod

class GradleExecutionSettingsUtilTest : GradleExecutionSettingsUtilTestCase() {

  fun `test filter generation by name`() {
    assertEquals("""--tests *""", createTestFilterFromPackage(""))
    assertEquals("""--tests *""", createTestFilter(null, null, null))
    assertEquals("""--tests "org.jetbrains.test.*"""", createTestFilterFromPackage("org.jetbrains.test"))
    assertEquals("""--tests "org.jetbrains.te*st.*"""", createTestFilterFromPackage("org.jetbrains.te\"st"))
    assertEquals("""--tests "org.jetbrains.te\st.*"""", createTestFilterFromPackage("org.jetbrains.te\\st"))
    assertEquals("""--tests "My favorite test case"""", createTestFilter("My favorite test case", null, null))
    assertEquals("""--tests "It isn't a favorite * test case"""", createTestFilter("It isn't a favorite \" test case", null, null))
    assertEquals("""--tests "Test case.it is my favorite test"""", createTestFilter("Test case", "it is my favorite test", null))
    assertEquals("""--tests "Test.it isn't a favorite * test"""", createTestFilter("Test", "it isn't a favorite . test", null))
  }

  fun `test filter generation for Java inner class`() {
    val psiFile = createFile("TestCase.java", """
      |package org.example;
      |
      |import org.junit.Test;
      |
      |public class TestCase {
      |  @Test public void test() {}
      |  @Test public void test0() {}
      |  @Test public void super_test() {}
      |  @Test public void super${'$'}test() {}
      |
      |  public static class InnerClass {
      |    @Test public void test1() {}
      |
      |    public static class InnerInnerClass {
      |      @Test public void test2() {}
      |    }
      |  }
      |}
    """.trimMargin())

    val testClass = psiFile.findChildByType<PsiClass>()
    assertClassTestFilter("""--tests "org.example.TestCase"""", testClass)

    val testMethods = testClass.findChildrenByType<PsiMethod>()
    assertMethodTestFilter("""--tests "org.example.TestCase.test"""", testMethods[0])
    assertMethodTestFilter("""--tests "org.example.TestCase.test0"""", testMethods[1])
    assertMethodTestFilter("""--tests "org.example.TestCase.super_test"""", testMethods[2])
    assertMethodTestFilter("""--tests "org.example.TestCase.super${'$'}test"""", testMethods[3])

    val innerClass = testClass.findChildByType<PsiClass>()
    assertClassTestFilter("""--tests "org.example.TestCase${'$'}InnerClass"""", innerClass)

    val innerMethod = innerClass.findChildByType<PsiMethod>()
    assertMethodTestFilter("""--tests "org.example.TestCase${'$'}InnerClass.test1"""", innerMethod)

    val innerInnerClass = innerClass.findChildByType<PsiClass>()
    assertClassTestFilter("""--tests "org.example.TestCase${'$'}InnerClass${'$'}InnerInnerClass"""", innerInnerClass)

    val innerInnerMethod = innerInnerClass.findChildByType<PsiMethod>()
    assertMethodTestFilter("""--tests "org.example.TestCase${'$'}InnerClass${'$'}InnerInnerClass.test2"""", innerInnerMethod)
  }

  fun `test filter generation for Groovy class`() {
    val psiFile = createFile("TestCase.groovy", """
      |package org.example
      |
      |import org.junit.Test
      |import spock.lang.Specification
      |
      |class TestCase {
      |  def test() {}
      |  def 'te st'() {}
      |  def 'tes\'t'() {}
      |  def 'tes\\\'t'() {}
      |  def 'tes\\t'() {}
      |  def 'tes\\\\t'() {}
      |  def 't\\es\\t'() {}
      |
      |  static class InnerClass extends Specification {
      |    def test1() {}
      |
      |    static class InnerInnerClass extends Specification {
      |      def test2() {}
      |    }
      |  }
      |}
    """.trimMargin())

    val testClass = psiFile.findChildByType<PsiClass>()
    assertClassTestFilter("""--tests "org.example.TestCase"""", testClass)

    val testClassBody = testClass.findChildByElementType("CLASS_BODY")
    val testMethods = testClassBody.findChildrenByType<PsiMethod>()
    assertMethodTestFilter("""--tests "org.example.TestCase.test"""", testMethods[0])
    assertMethodTestFilter("""--tests "org.example.TestCase.te st"""", testMethods[1])
    assertMethodTestFilter("""--tests "org.example.TestCase.tes't"""", testMethods[2])
    assertMethodTestFilter("""--tests "org.example.TestCase.tes\'t"""", testMethods[3])
    assertMethodTestFilter("""--tests "org.example.TestCase.tes\t"""", testMethods[4])
    assertMethodTestFilter("""--tests "org.example.TestCase.tes\\t"""", testMethods[5])
    assertMethodTestFilter("""--tests "org.example.TestCase.t\es\t"""", testMethods[6])

    val innerClass = testClassBody.findChildByType<PsiClass>()
    assertClassTestFilter("""--tests "org.example.TestCase${'$'}InnerClass"""", innerClass)

    val innerClassBody = innerClass.findChildByElementType("CLASS_BODY")
    val innerMethod = innerClassBody.findChildByType<PsiMethod>()
    assertMethodTestFilter("""--tests "org.example.TestCase${'$'}InnerClass.test1"""", innerMethod)

    val innerInnerClass = innerClassBody.findChildByType<PsiClass>()
    assertClassTestFilter("""--tests "org.example.TestCase${'$'}InnerClass${'$'}InnerInnerClass"""", innerInnerClass)

    val innerInnerClassBody = innerInnerClass.findChildByElementType("CLASS_BODY")
    val innerInnerMethod = innerInnerClassBody.findChildByType<PsiMethod>()
    assertMethodTestFilter("""--tests "org.example.TestCase${'$'}InnerClass${'$'}InnerInnerClass.test2"""", innerInnerMethod)
  }

  fun `test filter generation for the Junit 4 parametrized tests`() {
    val junitLibrary = IntelliJProjectConfiguration.getProjectLibrary("JUnit4")
    for (classesUrl in junitLibrary.classesUrls) {
      ModuleRootModificationUtil.addModuleLibrary(module, classesUrl)
    }
    requireResolvedJavaClass("org.junit.Test")
    requireResolvedJavaClass("org.junit.runner.RunWith")
    requireResolvedJavaClass("org.junit.runners.Parameterized")

    val psiFile = createFile("TestCase.java", """
      |package org.example;
      |
      |import org.junit.Test;
      |import org.junit.runner.RunWith;
      |import org.junit.runners.Parameterized;
      |
      |@RunWith(Parameterized.class)
      |public class TestCase {
      |
      |  @Test
      |  public void test() {}
      |}
    """.trimMargin())

    val testClass = psiFile.findChildByType<PsiClass>()
    val testMethod = testClass.findChildByType<PsiMethod>()
    val testClassLocation = PsiLocation.fromPsiElement(project, testClass)
    val testMethodLocation = PsiLocation.fromPsiElement(project, testMethod)

    assertTestFilter("--tests \"org.example.TestCase\"", testClassLocation, testClass, null)
    assertTestFilter("--tests \"org.example.TestCase.test[*]\"", testMethodLocation, testClass, testMethod)
  }

  fun `test filter generation for the Junit 5 parametrized tests`() {
    val junitLibrary = IntelliJProjectConfiguration.getProjectLibrary("JUnit5Params")
    for (classesUrl in junitLibrary.classesUrls) {
      ModuleRootModificationUtil.addModuleLibrary(module, classesUrl)
    }
    requireResolvedJavaClass("org.junit.jupiter.params.ParameterizedTest")
    requireResolvedJavaClass("org.junit.jupiter.params.provider.CsvSource")

    val psiFile = createFile("TestCase.java", """
      |package org.example;
      |
      |import org.junit.jupiter.params.ParameterizedTest;
      |import org.junit.jupiter.params.provider.CsvSource;
      |
      |public class TestCase {
      |
      |  @ParameterizedTest
      |  @CsvSource({"1, 'first'", "2, 'second'"})
      |  public void test(int value, String name) {}
      |}
    """.trimMargin())

    val testClass = psiFile.findChildByType<PsiClass>()
    val testMethod = testClass.findChildByType<PsiMethod>()
    val testClassLocation = PsiLocation.fromPsiElement(project, testClass)
    val testMethodLocation = PsiLocation.fromPsiElement(project, testMethod)

    assertTestFilter("--tests \"org.example.TestCase\"", testClassLocation, testClass, null)
    assertTestFilter("--tests \"org.example.TestCase.test\"", testMethodLocation, testClass, testMethod)
  }
}