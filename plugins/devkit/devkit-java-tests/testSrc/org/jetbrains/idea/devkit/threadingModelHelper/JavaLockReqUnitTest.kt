// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.util.io.NioFiles
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import java.nio.file.Files
import java.nio.file.Path
import java.util.EnumSet
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.milliseconds


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

  fun testStatisticsReport() {
    myFixture.configureByFile("AnnotationInChain.java")
    val targetMethod = findTestMethod(myFixture.file as PsiJavaFile, "AnnotationInChain")
    val statisticsDir = Files.createTempDirectory("lockReqStatistics")
    val reportedFiles = mutableListOf<Path>()
    try {
      runBlocking {
        analyzerBFS.analyzeMethodStreaming(SmartPointerManager.createPointer(targetMethod),
                                           AnalysisConfig.forProject(project, EnumSet.allOf(ConstraintType::class.java)),
                                           project,
                                           object : LockReqConsumer {
                                             override fun onStatisticsReport(file: Path) {
                                               reportedFiles.add(file)
                                             }
                                           },
                                           statisticsDir)
      }
      val reports = statisticsDir.listDirectoryEntries()
      assertEquals(1, reports.size)
      assertEquals(reports, reportedFiles)
      val report = reports.single().readText()
      assertTrue(report, report.contains("Root method: AnnotationInChain.testMethod()"))
      assertTrue(report, report.contains("Status: COMPLETED"))
      assertTrue(report, report.contains("Processed nodes: 3 "))

      // columns: phase, time, share, blocks, longest block
      val breakdownRows = report.lineSequence()
        .dropWhile { !it.startsWith("phase ") }
        .drop(1)
        .takeWhile { it != "Phases:" }
        .associate { line -> LockReqPhaseTimer.Phase.entries.first { line.startsWith(it.presentableName + " ") } to line }
      assertEquals(LockReqPhaseTimer.Phase.entries.toSet(), breakdownRows.keys)
      // testMethod and intermediateMethod resolve one call each
      assertEquals("2", blockCount(breakdownRows.getValue(LockReqPhaseTimer.Phase.REFERENCE_RESOLVE)))
      assertEquals("3", blockCount(breakdownRows.getValue(LockReqPhaseTimer.Phase.TREE_WALK)))
      assertEquals("3", blockCount(breakdownRows.getValue(LockReqPhaseTimer.Phase.ANNOTATION_CHECK)))
      assertEquals("2", blockCount(breakdownRows.getValue(LockReqPhaseTimer.Phase.CALLEE_CHECK)))
      // 3 nodes do 2 read actions each, and 2 nodes do 1 more read action for the callee
      assertEquals("8", blockCount(breakdownRows.getValue(LockReqPhaseTimer.Phase.READ_ACTION_WAIT)))
      assertTrue(report, report.contains("tree walk ") && report.contains("1x, longest "))

      // intermediateMethod and targetMethod are searched once each, and each search finds only the searched method
      assertTrue(report, report.contains("Inheritor searches: 2 (unique methods: 2, repeated searches: 0)"))
      val inheritorRows = tableRows(report, "Slowest classes for the inheritor search")
      // class columns: time, searches, methods, longest search, results, class
      val classRow = inheritorRows.first()
      assertEquals(listOf("2", "2", "2", "AnnotationInChain"), listOf(classRow[1], classRow[2], classRow[4], classRow[5]))
      // method columns: time, searches, longest search, results, method
      assertEquals(mapOf(
        "intermediateMethod()" to listOf("1", "1"),
        "targetMethod()" to listOf("1", "1"),
      ), inheritorRows.drop(1).associate { it[4] to listOf(it[1], it[3]) })
      assertEmpty(tableRows(report, "Slowest topic classes for the listener search"))

      // columns: time, depth, callees, subtreeDepth, subtreeNodes, subtreeTime, method
      val treeRows = tableRows(report, "Traversal tree")
        .associate { it[6] to listOf(it[1], it[2], it[3], it[4]) }
      assertEquals(mapOf(
        "AnnotationInChain.testMethod()" to listOf("1", "1", "3", "3"),
        "AnnotationInChain.intermediateMethod()" to listOf("2", "1", "2", "2"),
        "AnnotationInChain.targetMethod()" to listOf("3", "0", "1", "1"),
      ), treeRows)
    }
    finally {
      NioFiles.deleteRecursively(statisticsDir)
    }
  }

  fun testPhaseTimerExcludesNestedBlocks() {
    val timer = LockReqPhaseTimer()
    timer.measure(LockReqPhaseTimer.Phase.INHERITOR_SEARCH) {
      Thread.sleep(20)
      repeat(2) {
        timer.measure(LockReqPhaseTimer.Phase.ENQUEUE) {
          Thread.sleep(30)
        }
      }
    }
    val snapshot = timer.snapshot(0)
    assertEquals(1, snapshot.count(LockReqPhaseTimer.Phase.INHERITOR_SEARCH))
    assertEquals(2, snapshot.count(LockReqPhaseTimer.Phase.ENQUEUE))
    assertTrue(snapshot.time(LockReqPhaseTimer.Phase.ENQUEUE) >= 60.milliseconds)
    assertTrue(snapshot.maxTime(LockReqPhaseTimer.Phase.ENQUEUE) >= 30.milliseconds)
    val searchTime = snapshot.time(LockReqPhaseTimer.Phase.INHERITOR_SEARCH)
    assertTrue(searchTime.toString(), searchTime >= 20.milliseconds && searchTime < snapshot.time(LockReqPhaseTimer.Phase.ENQUEUE))
    assertEquals(0L, snapshot.timeNanos(LockReqPhaseTimer.Phase.OTHER))

    val total = snapshot.timeNanos(LockReqPhaseTimer.Phase.INHERITOR_SEARCH) + snapshot.timeNanos(LockReqPhaseTimer.Phase.ENQUEUE)
    assertEquals(1_000L, timer.snapshot(total + 1_000).timeNanos(LockReqPhaseTimer.Phase.OTHER))
  }

  /**
   * Splits the rows of the report table below the [title] line into columns.
   */
  private fun tableRows(report: String, title: String): List<List<String>> {
    return report.lineSequence()
      .dropWhile { !it.startsWith(title) }
      .dropWhile { !it.trimStart().startsWith("time ") }
      .drop(1)
      .takeWhile { it.isNotBlank() }
      .map { it.trim().split(Regex("\\s+")) }
      .toList()
  }

  private fun blockCount(breakdownRow: String): String = breakdownRow.trim().split(Regex("\\s+")).dropLast(1).last()

  private fun doTest(className: String): AnalysisResult {
    val fileName = "${getTestName(false)}.java"
    val psiJavaFile = myFixture.configureByFile(fileName) as PsiJavaFile
    val targetMethod = findTestMethod(psiJavaFile, className)
    return runBlocking {
      analyzerBFS.analyzeMethod(SmartPointerManager.createPointer(targetMethod), AnalysisConfig.forProject(psiJavaFile.project, EnumSet.allOf(ConstraintType::class.java)))
    }
  }

  private fun findTestMethod(psiJavaFile: PsiJavaFile, className: String): PsiMethod {
    val targetClass = psiJavaFile.classes.find { it.name == className } ?: error("Could not find class $className")
    return targetClass.methods.find { it.name == TEST_METHOD_NAME } ?: error("Could not find method $TEST_METHOD_NAME")
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
