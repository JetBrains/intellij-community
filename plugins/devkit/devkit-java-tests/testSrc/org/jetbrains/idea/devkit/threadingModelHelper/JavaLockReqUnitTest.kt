// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.psi.PsiJavaFile
import com.intellij.psi.SmartPointerManager
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import java.util.*


@TestDataPath($$"$CONTENT_ROOT/testData/threadingModelHelper/")
class JavaLockReqUnitTest : BasePlatformTestCase() {

  private val analyzerBFS = LockReqAnalyzerParallelBFS()

  override fun setUp() {
    super.setUp()
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
    val actualPathsBFS = formatResult(result)
    assertTrue(actualPathsBFS.isEmpty())
  }

  fun testAssertionInNestedBlock() {
    val result = doTest("AssertionInNestedBlock")
    val expectedPaths = listOf("AssertionInNestedBlock.testMethod => READ.ASSERTION")
    val actualPathsBFS = formatResult(result)
    assertEquals(expectedPaths.sorted(), actualPathsBFS.sorted())
  }

  fun testAnnotationInChain() {
    val result = doTest("AnnotationInChain")
    val expectedPaths = listOf("AnnotationInChain.testMethod -> AnnotationInChain.intermediateMethod" +
                               " -> AnnotationInChain.targetMethod => READ.ANNOTATION")
    val actualPathsBFS = formatResult(result)
    assertEquals(expectedPaths.sorted(), actualPathsBFS.sorted())
  }

  fun testBothAnnotationAndAssertion() {
    val result = doTest("BothAnnotationAndAssertion")
    val expectedPaths = listOf("BothAnnotationAndAssertion.testMethod => WRITE.ANNOTATION",
                               "BothAnnotationAndAssertion.testMethod => BGT.ASSERTION")
    val actualPathsBFS = formatResult(result)
    assertEquals(expectedPaths.sorted(), actualPathsBFS.sorted())
  }

  fun testCyclicRecursiveCalls() {
    val result = doTest("CyclicRecursiveCalls")
    val expectedPaths = listOf("CyclicRecursiveCalls.testMethod -> CyclicRecursiveCalls.methodB => READ.ANNOTATION")
    val actualPathsBFS = formatResult(result)
    assertEquals(expectedPaths.sorted(), actualPathsBFS.sorted())
  }

  fun testDifferentClassesMethods() {
    val result = doTest("DifferentClassesMethods")
    val expectedPaths = listOf("DifferentClassesMethods.testMethod -> DifferentClassesMethods.Helper.helperMethod -> DifferentClassesMethods.Service.serviceMethod => EDT.ANNOTATION",
                               "DifferentClassesMethods.testMethod -> DifferentClassesMethods.Helper.helperMethod => WRITE.ASSERTION")
    val actualPathsBFS = formatResult(result)
    assertEquals(expectedPaths.sorted(), actualPathsBFS.sorted())
  }

  fun testMultipleAssertionsInMethod() {
    val result = doTest("MultipleAssertionsInMethod")
    val expectedPaths = listOf("MultipleAssertionsInMethod.testMethod => READ.ASSERTION")
    val actualPathsBFS = formatResult(result)
    assertEquals(1, actualPathsBFS.size)
    assertEquals(expectedPaths.sorted(), actualPathsBFS.sorted())
  }


  fun testLambdaWithMethodReference() {
    val result = doTest("LambdaWithMethodReference")
    val expectedPaths = listOf("LambdaWithMethodReference.testMethod -> LambdaWithMethodReference.processItem => READ.ASSERTION")
    val actualPathsBFS = formatResult(result)
    assertEquals(expectedPaths.sorted(), actualPathsBFS.sorted())
  }

  fun testSubtypingPolymorphism() {
    val result = doTest("SubtypingPolymorphism")
    val expectedPaths = listOf(
      "SubtypingPolymorphism.testMethod -> FileService.execute => READ.ANNOTATION",
      "SubtypingPolymorphism.testMethod -> UIService.execute => EDT.ASSERTION",
      "SubtypingPolymorphism.testMethod -> DBService.execute => READ.ASSERTION"
    )
    val actualPathsBFS = formatResult(result)
    assertEquals(expectedPaths.sorted(), actualPathsBFS.sorted())
  }

  private fun doTest(className: String): AnalysisResult {
    val fileName = "${getTestName(false)}.java"
    val psiJavaFile = myFixture.configureByFile(fileName) as PsiJavaFile
    val targetClass = psiJavaFile.classes.find { it.name == className } ?: error("Could not find class $className")
    val targetMethod = targetClass.methods.find { it.name == TEST_METHOD_NAME } ?: error("Could not find method $TEST_METHOD_NAME")
    return runBlocking {
      analyzerBFS.analyzeMethod(SmartPointerManager.createPointer(targetMethod), AnalysisConfig.forProject(psiJavaFile.project, EnumSet.allOf(ConstraintType::class.java)))
    }
  }

  private fun formatResult(result: AnalysisResult): List<String> {
    val actualPaths = result.paths.map { path ->
      val chain = path.methodChain.joinToString(" -> ") { "${it.containingClassName}.${it.methodName}" }
      val requirement = "${path.lockRequirement.constraintType.name}.${path.lockRequirement.requirementReason.name}"
      "$chain => $requirement"
    }
    return actualPaths
  }

  companion object {
    private const val TEST_METHOD_NAME = "testMethod"
  }
}
