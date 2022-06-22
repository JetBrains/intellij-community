// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.filePrediction.predictor

import com.intellij.filePrediction.FilePredictionSessionManager
import com.intellij.filePrediction.FilePredictionTestDataHelper
import com.intellij.filePrediction.FilePredictionTestProjectBuilder
import com.intellij.filePrediction.candidates.FilePredictionNeighborFilesProvider
import com.intellij.filePrediction.candidates.FilePredictionReferenceProvider
import com.intellij.filePrediction.predictor.model.disableFilePredictionModel
import com.intellij.filePrediction.predictor.model.setConstantFilePredictionModel
import com.intellij.filePrediction.predictor.model.setCustomCandidateProviderModel
import com.intellij.filePrediction.predictor.model.setPredefinedProbabilityModel
import com.intellij.internal.statistic.FUCollectorTestCase.collectLogEvents
import com.intellij.internal.statistic.TestStatisticsEventValidatorBuilder
import com.intellij.internal.statistic.TestStatisticsEventsValidator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import com.intellij.util.PathUtil.getFileName
import java.util.concurrent.TimeUnit

class FileUsagePredictorLoggerTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

  private fun doTestOpenedFile(builder: FilePredictionTestProjectBuilder,
                               nextFilePath: String,
                               validator: TestFileCandidatesValidator,
                               expectedCandidates: Int) {
    val composite = TestStatisticsEventValidatorBuilder()
      .hasEventId("calculated")
      .contains("performance", "session", "candidates")
      .withCustom(TestFileCandidatesValidatorBuilder()
        .contains("opened", "prob")
        .withCandidateSize(expectedCandidates)
        .withCustom(FileOpenedValidator(true))
        .withCustom(validator).build()
      ).build()

    val expectedEvents = if (expectedCandidates == 0) 0 else 1
    doTest(builder, nextFilePath, expectedEvents, composite) {
      setConstantFilePredictionModel(0.1, it)
      FilePredictionSessionManager(5, 1, 3, 1.0)
    }
  }

  private fun doTestWithPredefinedProbability(builder: FilePredictionTestProjectBuilder,
                                              expectedCandidates: Int,
                                              probabilities: List<Double>,
                                              validator: FileProbabilityValidator,
                                              candidatesLimit: Int,
                                              logTopLimit: Int,
                                              logTotalLimit: Int) {
    assertTrue(
      "Number of provided probabilities should not be less than expected events",
      probabilities.size >= expectedCandidates
    )

    val composite = TestStatisticsEventValidatorBuilder()
      .hasEventId("calculated")
      .contains("performance", "session", "candidates")
      .withCustom(TestFileCandidatesValidatorBuilder()
        .contains("opened", "prob")
        .withCandidateSize(expectedCandidates)
        .withCustom(FileOpenedValidator(false))
        .withCustom(validator).build()
      ).build()

    val expectedEvents = if (expectedCandidates == 0) 0 else 1
    doTest(builder, null, expectedEvents, composite) {
      setPredefinedProbabilityModel(it, probabilities)
      FilePredictionSessionManager(candidatesLimit, logTopLimit, logTotalLimit, 1.0)
    }
  }

  private fun doTestWithConstant(builder: FilePredictionTestProjectBuilder, expectedCandidates: Int) {
    val validator = TestStatisticsEventValidatorBuilder()
      .hasEventId("calculated")
      .contains("session")
      .withCustom(TestFileCandidatesValidatorBuilder()
        .contains("prob")
        .withCandidateSize(expectedCandidates)
        .withCustom(FileOpenedValidator(false)).build()
      ).build()

    val expectedEvents = if (expectedCandidates == 0) 0 else 1
    doTest(builder, null, expectedEvents , validator) {
      setConstantFilePredictionModel(0.1, it)
      FilePredictionSessionManager(5, 1, 3, 1.0)
    }
  }

  private fun doTestWithoutModel(builder: FilePredictionTestProjectBuilder, expectedCandidates: Int) {
    val validator = TestStatisticsEventValidatorBuilder()
      .hasEventId("calculated")
      .contains("session")
      .withCustom(TestFileCandidatesValidatorBuilder()
        .notContains("prob")
        .withCandidateSize(expectedCandidates)
        .withCustom(FileOpenedValidator(false)).build()
      ).build()

    val expectedEvents = if (expectedCandidates == 0) 0 else 1
    doTest(builder, null, expectedEvents, validator) {
      disableFilePredictionModel()
      FilePredictionSessionManager(5, 1, 3, 1.0)
    }
  }

  private fun doTest(builder: FilePredictionTestProjectBuilder,
                     nextFilePath: String?,
                     expectedEvents: Int,
                     validator: TestStatisticsEventsValidator,
                     predictorProvider: (Disposable) -> FilePredictionSessionManager) {
    val usedNextFilePath = nextFilePath ?: "com/bar/baz/foo/next_file.txt"
    val root = builder.addFile(usedNextFilePath).create(myFixture)
    assertNotNull("Cannot create test project", root)

    val file = FilePredictionTestDataHelper.findMainTestFile(root)
    assertNotNull("Cannot find main project file", file)

    val nextFileName = FileUtilRt.getNameWithoutExtension(getFileName(usedNextFilePath))
    val nextFile = FilePredictionTestDataHelper.findChildRecursively(nextFileName, root)
    assertNotNull("Cannot find next file", nextFile)

    setCustomCandidateProviderModel(testRootDisposable, FilePredictionReferenceProvider(), FilePredictionNeighborFilesProvider())
    val predictor = predictorProvider.invoke(testRootDisposable)
    val events = collectLogEvents {
      ApplicationManager.getApplication().executeOnPooledThread{
        predictor.onSessionStarted(myFixture.project, file!!)
        predictor.onSessionStarted(myFixture.project, nextFile!!)
      }.get(1, TimeUnit.SECONDS)
    }
    val candidateEvents = events.filter { it.event.id == "calculated" }
    assertEquals(expectedEvents, candidateEvents.size)

    if (candidateEvents.isNotEmpty()) {
      validator.validateAll(candidateEvents)
    }
  }

  fun `test no candidates in empty project`() {
    val builder = FilePredictionTestProjectBuilder("com")
    doTestWithConstant(builder, 0)
  }

  fun `test candidates less than limit`() {
    val builder =
      FilePredictionTestProjectBuilder("com/test").addFiles(
        "com/test/Foo.txt",
        "com/test/Bar.txt"
      )
    doTestWithConstant(builder, 2)
  }

  fun `test candidates more than log limit`() {
    val builder =
      FilePredictionTestProjectBuilder("com/test").addFiles(
        "com/test/Foo1.txt",
        "com/test/Foo2.txt",
        "com/test/Foo3.txt",
        "com/test/Foo4.txt"
      )
    doTestWithConstant(builder, 3)
  }

  fun `test candidates more than limit`() {
    val builder =
      FilePredictionTestProjectBuilder("com/test").addFiles(
        "com/test/Foo1.txt",
        "com/test/Foo2.txt",
        "com/test/Foo3.txt",
        "com/test/Foo4.txt",
        "com/test/Foo5.txt",
        "com/test/Foo6.txt",
        "com/test/Foo7.txt"
      )
    doTestWithConstant(builder, 3)
  }

  fun `test candidates without model`() {
    val builder =
      FilePredictionTestProjectBuilder("com/test").addFiles(
        "com/test/Foo.txt",
        "com/test/Bar.txt"
      )
    doTestWithoutModel(builder, 2)
  }

  fun `test candidates more than log limit without model`() {
    val builder =
      FilePredictionTestProjectBuilder("com/test").addFiles(
        "com/test/Foo1.txt",
        "com/test/Foo2.txt",
        "com/test/Foo3.txt",
        "com/test/Foo4.txt",
        "com/test/Foo5.txt"
      )
    doTestWithoutModel(builder, 3)
  }

  fun `test only top candidates logged`() {
    val builder =
      FilePredictionTestProjectBuilder("com/test").addFiles(
        "com/test/Foo1.txt",
        "com/test/Foo2.txt",
        "com/test/Foo3.txt",
        "com/test/Foo4.txt",
        "com/test/Foo5.txt"
      )

    val probabilities = listOf(0.9, 0.8, 0.7, 0.6, 0.5).shuffled()
    val validator = FileProbabilityValidator(listOf(0.9, 0.8, 0.7), emptyList())
    doTestWithPredefinedProbability(builder, 3, probabilities, validator, 5, 3, 3)
  }

  fun `test no top candidates logged`() {
    val builder =
      FilePredictionTestProjectBuilder("com/test").addFiles(
        "com/test/Foo1.txt",
        "com/test/Foo2.txt",
        "com/test/Foo3.txt",
        "com/test/Foo4.txt",
        "com/test/Foo5.txt"
      )

    val probabilities = listOf(0.9, 0.8, 0.7, 0.6, 0.5).shuffled()
    val validator = FileProbabilityValidator(emptyList(), listOf(0.9, 0.8, 0.7, 0.6, 0.5))
    doTestWithPredefinedProbability(builder, 4, probabilities, validator, 5, 0, 4)
  }

  fun `test top and random candidates probabilities logged`() {
    val builder =
      FilePredictionTestProjectBuilder("com/test").addFiles(
        "com/test/Foo1.txt",
        "com/test/Foo2.txt",
        "com/test/Foo3.txt",
        "com/test/Foo4.txt",
        "com/test/Foo5.txt"
      )

    val probabilities = listOf(0.9, 0.8, 0.7, 0.6, 0.5).shuffled()
    val validator = FileProbabilityValidator(listOf(0.9), listOf(0.8, 0.7, 0.6, 0.5))
    doTestWithPredefinedProbability(builder, 3, probabilities, validator, 5, 1, 3)
  }

  fun `test multiple top and random candidates probabilities logged`() {
    val builder =
      FilePredictionTestProjectBuilder("com/test").addFiles(
        "com/test/Foo1.txt",
        "com/test/Foo2.txt",
        "com/test/Foo3.txt",
        "com/test/Foo4.txt",
        "com/test/Foo5.txt",
        "com/test/Foo6.txt",
        "com/test/Foo7.txt"
      )

    val probabilities = listOf(0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3).shuffled()
    val validator = FileProbabilityValidator(listOf(0.9, 0.8), listOf(0.7, 0.6, 0.5, 0.4, 0.3))
    doTestWithPredefinedProbability(builder, 5, probabilities, validator, 7, 2, 5)
  }

  fun `test opened file has opened field`() {
    val builder = FilePredictionTestProjectBuilder("com")

    val validator = TestFileCandidatesValidatorBuilder()
      .hasField("opened", 1).build()
    doTestOpenedFile(builder, "com/next_file.txt", validator, 1)
  }

  fun `test candidate and opened files has opened field`() {
    val builder = FilePredictionTestProjectBuilder("com/test").addFiles(
      "com/test/Foo1.txt",
      "com/test/Foo2.txt",
      "com/test/Foo3.txt"
    )

    @Suppress("UNCHECKED_CAST")
    val validator = TestFileCandidatesValidatorBuilder()
      .hasField("opened", 0) { (it["features"] as String).contains("JAVA").not() }
      .hasField("opened", 1) { (it["features"] as String).contains("JAVA") }.build()
    doTestOpenedFile(builder, "com/test/next_file.java", validator, 4)
  }

  fun `test candidate and opened files with the same type has opened field`() {
    val builder = FilePredictionTestProjectBuilder("com/test").addFiles(
      "com/test/Foo1.java",
      "com/test/Foo2.java",
      "com/test/Foo3.java"
    )

    val validator = TestFileCandidatesValidatorBuilder().build()
    doTestOpenedFile(builder, "com/test/next_file.java", validator, 4)
  }
}

private class FileProbabilityValidator(val top: List<Double>, val rest: List<Double>) : TestFileCandidatesValidator() {
  override fun validateCandidates(candidates: List<Map<String, Any>>) {
    val actualProbabilities = candidates.map { it["prob"] as Double }

    val actualTop = actualProbabilities.subList(0, top.size)
    CodeInsightFixtureTestCase.assertEquals("Top candidates probabilities is different from expected", top, actualTop)

    for (probability in actualProbabilities.subList(top.size, actualProbabilities.size)) {
      CodeInsightFixtureTestCase.assertTrue("Unknown probability in rest of candidates", rest.contains(probability))
    }
  }
}

private class FileOpenedValidator(val hasOpenFile: Boolean) : TestFileCandidatesValidator() {
  override fun validateCandidates(candidates: List<Map<String, Any>>) {
    val openedFiles = candidates.filter { it["opened"] == 1 }.size
    CodeInsightFixtureTestCase.assertTrue("Number of opened files is greater than 1", openedFiles <= 1)
    if (hasOpenFile) {
      CodeInsightFixtureTestCase.assertTrue("No file opened event", openedFiles == 1)
    }
    else {
      CodeInsightFixtureTestCase.assertTrue("Has file opened event but shouldn't", openedFiles == 0)
    }
  }
}