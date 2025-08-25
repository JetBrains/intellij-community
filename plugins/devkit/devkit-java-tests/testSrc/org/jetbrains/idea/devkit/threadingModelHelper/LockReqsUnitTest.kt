// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil


@TestDataPath($$"$CONTENT_ROOT/testData/threadingModelHelper/")
class LockReqsUnitTest : BasePlatformTestCase() {

  private lateinit var analyzer: LockReqsAnalyzerDFS

  override fun setUp() {
    super.setUp()
    analyzer = LockReqsAnalyzerDFS()
    myFixture.addFileToProject("com/intellij/util/concurrency/annotations.java", """
        package com.intellij.util.concurrency.annotations;
        public @interface RequiresReadLock {}
        public @interface RequiresWriteLock {}
        public @interface RequiresEdt {}
        public @interface RequiresBackgroundThread {}
        public @interface RequiresReadLockAbsence {}
        """.trimIndent())
    myFixture.addFileToProject("com/intellij/util/concurrency/ThreadingAssertions.java", """
      package com.intellij.util.concurrency;
      public class ThreadingAssertions {
        public static void assertReadAccess() {}
        public static void assertWriteAccess() {}
        public static void assertWriteIntentReadAccess() {}
        public static void assertEventDispatchThread() {}
        public static void assertBackgroundThread() {}
      }
      """.trimIndent())
    myFixture.addFileToProject("mock/MessageBus.java", """
      package com.intellij.util.concurrency;
      public interface MessageBus {
        <L> L syncPublisher(Class<L> topic);
      }
      """.trimIndent())
  }

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "threadingModelHelper/"

  fun testNoLockRequirements() {
    val result = doTest("NoLockRequirements")
    val actualPaths = formatResult(result)
    assertTrue(actualPaths.isEmpty())
  }

  fun testAssertionInNestedBlock() {
    val result = doTest("AssertionInNestedBlock")
    val expectedPaths = listOf("AssertionInNestedBlock.testMethod => READ.ASSERTION")
    val actualPaths = formatResult(result)
    assertEquals(expectedPaths.sorted(), actualPaths.sorted())
  }

  fun testAnnotationInChain() {
    val result = doTest("AnnotationInChain")
    val expectedPaths = listOf("AnnotationInChain.testMethod -> AnnotationInChain.intermediateMethod" +
                               " -> AnnotationInChain.targetMethod => READ.ANNOTATION")
    val actualPaths = formatResult(result)
    assertEquals(expectedPaths.sorted(), actualPaths.sorted())
  }

  fun testBothAnnotationAndAssertion() {
    val result = doTest("BothAnnotationAndAssertion")
    val expectedPaths = listOf("BothAnnotationAndAssertion.testMethod => WRITE.ANNOTATION",
                               "BothAnnotationAndAssertion.testMethod => BGT.ASSERTION")
    val actualPaths = formatResult(result)
    assertEquals(expectedPaths.sorted(), actualPaths.sorted())
  }

  fun testCyclicRecursiveCalls() {
    val result = doTest("CyclicRecursiveCalls")
    val expectedPaths = listOf("CyclicRecursiveCalls.testMethod -> CyclicRecursiveCalls.methodB => READ.ANNOTATION")
    val actualPaths = formatResult(result)
    assertEquals(expectedPaths.sorted(), actualPaths.sorted())
  }

  fun testDifferentClassesMethods() {
    val result = doTest("DifferentClassesMethods")
    val expectedPaths = listOf("DifferentClassesMethods.testMethod -> Helper.helperMethod -> Service.serviceMethod => EDT.ANNOTATION",
                               "DifferentClassesMethods.testMethod -> Helper.helperMethod => WRITE.ASSERTION")
    val actualPaths = formatResult(result)
    assertEquals(expectedPaths.sorted(), actualPaths.sorted())
  }

  fun testMultipleAssertionsInMethod() {
    val result = doTest("MultipleAssertionsInMethod")
    val expectedPaths = listOf("MultipleAssertionsInMethod.testMethod => READ.ASSERTION")
    val actualPaths = formatResult(result)
    assertEquals(1, actualPaths.size)
    assertEquals(expectedPaths.sorted(), actualPaths.sorted())
  }


  fun testLambdaWithMethodReference() {
    val result = doTest("LambdaWithMethodReference")
    val expectedPaths = listOf("LambdaWithMethodReference.testMethod -> LambdaWithMethodReference.processItem => READ.ASSERTION")
    val actualPaths = formatResult(result)
    assertEquals(expectedPaths.sorted(), actualPaths.sorted())

  }

  fun testSubtypingPolymorphism() {
    val result = doTest("SubtypingPolymorphism")
    val expectedPaths = listOf(
      "SubtypingPolymorphism.testMethod -> FileService.execute => READ.ANNOTATION",
      "SubtypingPolymorphism.testMethod -> UIService.execute => EDT.ASSERTION",
      "SubtypingPolymorphism.testMethod -> DBService.execute => READ.ASSERTION"
    )
    val actualPaths = formatResult(result)
    assertEquals(expectedPaths.sorted(), actualPaths.sorted())
  }

  private fun doTest(className: String): AnalysisResult {
    val fileName = "${getTestName(false)}.java"
    val psiJavaFile = myFixture.configureByFile(fileName) as PsiJavaFile
    val targetClass = psiJavaFile.classes.find { it.name == className } ?: error("Could not find class $className")
    val targetMethod = targetClass.methods.find { it.name == testMethodName } ?: error("Could not find method $testMethodName")
    return analyzer.analyzeMethod(targetMethod)
  }

  private fun formatResult(result: AnalysisResult): List<String> {
    val actualPaths = result.paths.map { path ->
      val chain = path.methodChain.joinToString(" -> ") { "${it.method.containingClass?.name}.${it.method.name}" }
      val requirement = "${path.lockRequirement.lockType.name}.${path.lockRequirement.requirementReason.name}"
      "$chain => $requirement"
    }
    return actualPaths
  }

  companion object {
    private const val testMethodName = "testMethod"
  }
}
