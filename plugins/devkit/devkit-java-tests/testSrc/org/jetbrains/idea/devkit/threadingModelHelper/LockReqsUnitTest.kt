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
    myFixture.addFileToProject("mock/RequiresReadLock.java", """
        package mock;
        public @interface RequiresReadLock {}
        """.trimIndent())
    myFixture.addFileToProject("mock/ThreadingAssertions.java", """
      package mock;
      public class ThreadingAssertions {
        public static void assertReadAccess() {}
      }
      """.trimIndent())
  }

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "threadingModelHelper/"

  /*
  fun testNoLockRequirements() {
    doTest("NoLockRequirements", "testMethod", emptyList())
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
  }*/

  private fun doTest(className: String, methodName: String, expectedPaths: List<String>) {
    val fileName = "${getTestName(false)}.java"
    val psiJavaFile = myFixture.configureByFile(fileName) as PsiJavaFile
    val targetClass = psiJavaFile.classes.find { it.name == className } ?: error("Could not find class $className")
    val targetMethod = targetClass.methods.find { it.name == className } ?: error("Could not find method $methodName")

    val result = analyzer.analyzeMethod(targetMethod)
    val actualPaths = result.paths.map { path ->
      buildString {
        path.methodChain.joinToString(separator = " -> ", postfix = " -> ") {
          "${it.method.containingClass?.name}.${it.method.name}"
        }
        "@${path.lockRequirement.lockType}"
      }
    }
    assertEquals(expectedPaths, actualPaths)
  }
}
