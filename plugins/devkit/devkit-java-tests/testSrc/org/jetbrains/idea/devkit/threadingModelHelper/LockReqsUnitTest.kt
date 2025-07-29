// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil


@TestDataPath($$"$CONTENT_ROOT/testData/threadingModelHelper/")
class LockReqsUnitTest : BasePlatformTestCase() {

  private lateinit var analyzer: LockReqsAnalyzer

  override fun setUp() {
    super.setUp()
    analyzer = LockReqsAnalyzer()
    myFixture.addFileToProject("testutils/RequiresReadLock.java", """
        package testutils;
        public @interface RequiresReadLock {}
        """.trimIndent())
    myFixture.addFileToProject("testutils/ThreadingAssertions.java", """
      package testutils;
      public class ThreadingAssertions {
        public static void assertReadAccess() {}
      }
      """.trimIndent())
    myFixture.addFileToProject("testutils/ExpectedPath.java", """
      package testutils;
      import java.lang.annotation.*;
      @Target(ElementType.TYPE)
      @Retention(RetentionPolicy.RUNTIME)
      public @interface ExpectedPath {
        String value();
      }
      """.trimIndent())
  }

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "threadingModelHelper/"

  fun testNoLockRequirements() {
    doTest()
  }

  fun testAnnotationInChain() {
    doTest()
  }

  fun testAssertionInNestedBlock() {
    doTest()
  }

  fun testBothAnnotationAndAssertion() {
    doTest()
  }

  fun testCyclicRecursiveCalls() {
    doTest()
  }

  fun testMethodsInDifferentClasses() {
    doTest()
  }

  fun testMultipleAssertionsInMethod() {
    doTest()
  }

  fun testLambdaWithMethodReference() {
    doTest()
  }

  private fun doTest() {
    val fileName = "${getTestName(false)}.java"
    val sourceMethodName = "testMethod"
    val psiJavaFile = myFixture.configureByFile(fileName) as PsiJavaFile

    val testClass = psiJavaFile.classes.first()
    val expectedPaths = testClass.annotations
      .filter { it.qualifiedName == "test.ExpectedPath" }
      .mapNotNull { it.findAttributeValue("value")?.text?.removeSurrounding("\"") }
      .sorted()

    val sourceMethod = testClass.findMethodsByName(sourceMethodName, false).first()
    val actualPaths = analyzer.analyzeMethod(sourceMethod).map { it.pathString }.sorted()

    assertEquals(expectedPaths, actualPaths)
  }
}
