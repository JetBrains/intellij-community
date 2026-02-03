// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.threadingModelHelper

import com.intellij.openapi.application.PluginPathManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.devkit.threadingModelHelper.AnalysisConfig
import org.jetbrains.idea.devkit.threadingModelHelper.AnalysisResult
import org.jetbrains.idea.devkit.threadingModelHelper.ConstraintType
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqAnalyzerParallelBFS
import java.util.EnumSet

@TestDataPath($$"$CONTENT_ROOT/testData/threadingModelHelper/")
class KtLockReqUnitTest : BasePlatformTestCase() {

  private val analyzerBFS: LockReqAnalyzerParallelBFS = LockReqAnalyzerParallelBFS()

  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject("com/intellij/util/concurrency/annotations/RequiresReadLock.java", """
        package com.intellij.util.concurrency.annotations;
        public @interface RequiresReadLock {}
        """.trimIndent())
    myFixture.addFileToProject("com/intellij/util/concurrency/annotations/RequiresWriteLock.java", """
        package com.intellij.util.concurrency.annotations;
        public @interface RequiresWriteLock {}
        """.trimIndent())
    myFixture.addFileToProject("com/intellij/util/concurrency/annotations/RequiresEdt.java", """
        package com.intellij.util.concurrency.annotations;
        public @interface RequiresEdt {}
        """.trimIndent())
    myFixture.addFileToProject("com/intellij/util/concurrency/annotations/RequiresBackgroundThread.java", """
        package com.intellij.util.concurrency.annotations;
        public @interface RequiresBackgroundThread {}
        """.trimIndent())
    myFixture.addFileToProject("com/intellij/util/concurrency/annotations/RequiresReadLockAbsence.java", """
        package com.intellij.util.concurrency.annotations;
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
    myFixture.addFileToProject("com/intellij/util/messages/MessageBus.java", """
      package com.intellij.util.messages;
      public interface MessageBus {
        <L> L syncPublisher(Class<L> topic);
      }
      """.trimIndent())
  }

  override fun getBasePath() = PluginPathManager.getPluginHomePathRelative("devkit") + "/devkit-kotlin-fir-tests/testData/threadingModelHelper/"

  private fun formatResult(result: AnalysisResult): List<String> {
    val actualPaths = result.paths.map { path ->
      val chain = path.methodChain.joinToString(" -> ") { "${it.containingClassName}.${it.methodName}" }
      val requirement = "${path.lockRequirement.constraintType.name}.${path.lockRequirement.requirementReason.name}"
      "$chain => $requirement"
    }
    return actualPaths
  }

  private fun doKotlinTest(relativeFileName: String): AnalysisResult {
    myFixture.configureByFile(relativeFileName)
    val className = relativeFileName.removeSuffix(".kt")
    val psiClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.projectScope(project))
    val method = SmartPointerManager.createPointer(psiClass?.methods?.firstOrNull { m -> m.name == TEST_METHOD_NAME }!!)
    val config = AnalysisConfig.Companion.forProject(project, EnumSet.allOf(ConstraintType::class.java))
    return runBlocking {
        analyzerBFS.analyzeMethod(method, config)
    }
  }

  fun testNoLockRequirementsKt() {
    val result = doKotlinTest("NoLockRequirements.kt")
    assertTrue(formatResult(result).isEmpty())
  }

  fun testAssertionInNestedBlockKt() {
    val result = doKotlinTest("AssertionInNestedBlock.kt")
    val expected = listOf("AssertionInNestedBlock.testMethod => READ.ASSERTION")
    assertEquals(expected.sorted(), formatResult(result).sorted())
  }

  fun testAnnotationInChainKt() {
    val result = doKotlinTest("AnnotationInChain.kt")
    val expected = listOf(
      "AnnotationInChain.testMethod -> AnnotationInChain.intermediateMethod -> AnnotationInChain.targetMethod => READ.ANNOTATION"
    )
    assertEquals(expected.sorted(), formatResult(result).sorted())
  }

  fun testLambdaWithMethodReferenceKt() {
    val result = doKotlinTest("LambdaWithMethodReference.kt")
    val expected = listOf(
      "LambdaWithMethodReference.testMethod -> LambdaWithMethodReference.processItem => READ.ASSERTION"
    )
    assertEquals(expected.sorted(), formatResult(result).sorted())
  }

  fun testTopLevelAndExtensionKt() {
    val result = doKotlinTest("TopLevelAndExtension.kt")
    val expected = listOf(
      "TopLevelAndExtension.testMethod -> TopLevelAndExtension.topLevel => READ.ASSERTION",
      "TopLevelAndExtension.testMethod -> TopLevelAndExtension.extFun => READ.ANNOTATION"
    )
    assertEquals(expected.sorted(), formatResult(result).sorted())
  }

  fun testBothAnnotationAndAssertionKt() {
    val result = doKotlinTest("BothAnnotationAndAssertion.kt")
    val expected = listOf(
      "BothAnnotationAndAssertion.testMethod => WRITE.ANNOTATION",
      "BothAnnotationAndAssertion.testMethod => BGT.ASSERTION"
    )
    assertEquals(expected.sorted(), formatResult(result).sorted())
  }

  fun testCyclicRecursiveCallsKt() {
    val result = doKotlinTest("CyclicRecursiveCalls.kt")
    val expected = listOf(
      "CyclicRecursiveCalls.testMethod -> CyclicRecursiveCalls.methodA -> CyclicRecursiveCalls.methodB => READ.ANNOTATION"
    )
    assertEquals(expected.sorted(), formatResult(result).sorted())
  }

  fun testDifferentClassesMethodsKt() {
    val result = doKotlinTest("DifferentClassesMethods.kt")
    val expected = listOf(
      "DifferentClassesMethods.testMethod -> Helper.helperMethod => WRITE.ASSERTION",
      "DifferentClassesMethods.testMethod -> Helper.helperMethod -> Service.serviceMethod => EDT.ANNOTATION"
    )
    assertEquals(expected.sorted(), formatResult(result).sorted())
  }

  fun testMultipleAssertionsInMethodKt() {
    val result = doKotlinTest("MultipleAssertionsInMethod.kt")
    val expected = listOf(
      "MultipleAssertionsInMethod.testMethod => READ.ASSERTION"
    )
    assertEquals(expected.sorted(), formatResult(result).sorted())
  }

  fun testSubtypingPolymorphismKt() {
    val result = doKotlinTest("SubtypingPolymorphism.kt")
    val expected = listOf(
      "SubtypingPolymorphism.testMethod -> FileService.execute => READ.ANNOTATION",
      "SubtypingPolymorphism.testMethod -> UIService.execute => EDT.ASSERTION",
      "SubtypingPolymorphism.testMethod -> DBService.execute => READ.ASSERTION"
    )
    assertEquals(expected.sorted(), formatResult(result).sorted())
  }

  fun testSuspendAndPropertyKt() {
    val result = doKotlinTest("SuspendAndProperty.kt")
    val expected = listOf(
      "SuspendAndProperty.testMethod -> SuspendAndProperty.getFromProperty => READ.ASSERTION",
      "SuspendAndProperty.testMethod -> SuspendAndProperty.susp => READ.ASSERTION"
    )
    assertEquals(expected.sorted(), formatResult(result).sorted())
  }

  fun testCompanionDefaultArgsKt() {
    val result = doKotlinTest("CompanionDefaultArgs.kt")
    val expected = listOf(
      "CompanionDefaultArgs.testMethod -> CompanionDefaultArgs.Companion.annoFun => READ.ANNOTATION",
      "CompanionDefaultArgs.testMethod -> CompanionDefaultArgs.defaultArgFun => READ.ASSERTION"
    )
    assertEquals(expected.sorted(), formatResult(result).sorted())
  }

  companion object {
    private const val TEST_METHOD_NAME = "testMethod"
  }
}