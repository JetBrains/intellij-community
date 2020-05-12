package com.intellij.filePrediction.predictor

import com.intellij.filePrediction.FilePredictionTestDataHelper
import com.intellij.filePrediction.FilePredictionTestProjectBuilder
import com.intellij.filePrediction.predictor.model.disableFilePredictionModel
import com.intellij.filePrediction.predictor.model.setConstantFilePredictionModel
import com.intellij.filePrediction.predictor.model.setPredefinedProbabilityModel
import com.intellij.internal.statistic.FUCounterCollectorTestCase.collectLogEvents
import com.intellij.internal.statistic.TestStatisticsEventValidatorBuilder
import com.intellij.internal.statistic.TestStatisticsEventsValidator
import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.openapi.Disposable
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture
import org.junit.Test

class FileUsagePredictorLoggerTest : CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

  private fun doTestWithPredefinedProbability(builder: FilePredictionTestProjectBuilder,
                                              expectedEvents: Int,
                                              probabilities: List<Double>,
                                              predictor: FileUsagePredictor,
                                              validator: FileProbabilityValidator) {
    assertTrue(
      "Number of provided probabilities should not be less than expected events",
      probabilities.size >= expectedEvents
    )

    val composite = TestStatisticsEventValidatorBuilder()
      .hasEventId("candidate.calculated")
      .contains("probability", "session_id")
      .withCustom(validator).build()

    doTest(builder, predictor, expectedEvents, composite) { setPredefinedProbabilityModel(it, probabilities) }
  }

  private fun doTestWithConstant(builder: FilePredictionTestProjectBuilder, expectedEvents: Int) {
    val predictor = FileUsagePredictor(5, 1, 3)
    val validator = TestStatisticsEventValidatorBuilder()
      .hasEventId("candidate.calculated")
      .contains("probability", "session_id").build()

    doTest(builder, predictor, expectedEvents, validator) { setConstantFilePredictionModel(0.1, it) }
  }

  private fun doTestWithoutModel(builder: FilePredictionTestProjectBuilder, expectedEvents: Int) {
    val predictor = FileUsagePredictor(5, 1, 3)
    val validator = TestStatisticsEventValidatorBuilder()
      .hasEventId("candidate.calculated")
      .contains("session_id")
      .notContains("probability").build()

    doTest(builder, predictor, expectedEvents, validator) { disableFilePredictionModel() }
  }

  private fun doTest(builder: FilePredictionTestProjectBuilder,
                     predictor: FileUsagePredictor,
                     expectedEvents: Int,
                     validator: TestStatisticsEventsValidator,
                     modelConfigurator: (Disposable) -> Unit) {
    val root = builder.create(myFixture)
    assertNotNull("Cannot create test project", root)

    val file = FilePredictionTestDataHelper.findMainTestFile(root)
    assertNotNull("Cannot find main project file", file)

    modelConfigurator.invoke(testRootDisposable)
    val events = collectLogEvents {
      predictor.predictNextFile(myFixture.project, 1, file!!)
    }
    val candidateEvents = events.filter { it.event.id == "candidate.calculated" }
    assertEquals(expectedEvents, candidateEvents.size)

    validator.validateAll(candidateEvents)
  }

  @Test
  fun `test no candidates in empty project`() {
    val builder =
      FilePredictionTestProjectBuilder().addMainFile("com")
    doTestWithConstant(builder, 0)
  }

  @Test
  fun `test candidates less than limit`() {
    val builder =
      FilePredictionTestProjectBuilder().addMainFile("com/test").addFiles(
        "com/test/Foo.txt",
        "com/test/Bar.txt"
      )
    doTestWithConstant(builder, 2)
  }

  @Test
  fun `test candidates more than log limit`() {
    val builder =
      FilePredictionTestProjectBuilder().addMainFile("com/test").addFiles(
        "com/test/Foo1.txt",
        "com/test/Foo2.txt",
        "com/test/Foo3.txt",
        "com/test/Foo4.txt"
      )
    doTestWithConstant(builder, 3)
  }

  @Test
  fun `test candidates more than limit`() {
    val builder =
      FilePredictionTestProjectBuilder().addMainFile("com/test").addFiles(
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

  @Test
  fun `test candidates without model`() {
    val builder =
      FilePredictionTestProjectBuilder().addMainFile("com/test").addFiles(
        "com/test/Foo.txt",
        "com/test/Bar.txt"
      )
    doTestWithoutModel(builder, 2)
  }

  @Test
  fun `test candidates more than log limit without model`() {
    val builder =
      FilePredictionTestProjectBuilder().addMainFile("com/test").addFiles(
        "com/test/Foo1.txt",
        "com/test/Foo2.txt",
        "com/test/Foo3.txt",
        "com/test/Foo4.txt",
        "com/test/Foo5.txt"
      )
    doTestWithoutModel(builder, 3)
  }

  @Test
  fun `test only top candidates logged`() {
    val builder =
      FilePredictionTestProjectBuilder().addMainFile("com/test").addFiles(
        "com/test/Foo1.txt",
        "com/test/Foo2.txt",
        "com/test/Foo3.txt",
        "com/test/Foo4.txt",
        "com/test/Foo5.txt"
      )

    val probabilities = listOf(0.9, 0.8, 0.7, 0.6, 0.5).shuffled()
    val predictor = FileUsagePredictor(5, 3, 3)
    val validator = FileProbabilityValidator(listOf(0.9, 0.8, 0.7), emptyList())
    doTestWithPredefinedProbability(builder, 3, probabilities, predictor, validator)
  }

  @Test
  fun `test no top candidates logged`() {
    val builder =
      FilePredictionTestProjectBuilder().addMainFile("com/test").addFiles(
        "com/test/Foo1.txt",
        "com/test/Foo2.txt",
        "com/test/Foo3.txt",
        "com/test/Foo4.txt",
        "com/test/Foo5.txt"
      )

    val probabilities = listOf(0.9, 0.8, 0.7, 0.6, 0.5).shuffled()
    val predictor = FileUsagePredictor(5, 0, 4)
    val validator = FileProbabilityValidator(emptyList(), listOf(0.9, 0.8, 0.7, 0.6, 0.5))
    doTestWithPredefinedProbability(builder, 4, probabilities, predictor, validator)
  }

  @Test
  fun `test top and random candidates probabilities logged`() {
    val builder =
      FilePredictionTestProjectBuilder().addMainFile("com/test").addFiles(
        "com/test/Foo1.txt",
        "com/test/Foo2.txt",
        "com/test/Foo3.txt",
        "com/test/Foo4.txt",
        "com/test/Foo5.txt"
      )

    val probabilities = listOf(0.9, 0.8, 0.7, 0.6, 0.5).shuffled()
    val predictor = FileUsagePredictor(5, 1, 3)
    val validator = FileProbabilityValidator(listOf(0.9), listOf(0.8, 0.7, 0.6, 0.5))
    doTestWithPredefinedProbability(builder, 3, probabilities, predictor, validator)
  }

  @Test
  fun `test multiple top and random candidates probabilities logged`() {
    val builder =
      FilePredictionTestProjectBuilder().addMainFile("com/test").addFiles(
        "com/test/Foo1.txt",
        "com/test/Foo2.txt",
        "com/test/Foo3.txt",
        "com/test/Foo4.txt",
        "com/test/Foo5.txt",
        "com/test/Foo6.txt",
        "com/test/Foo7.txt"
      )

    val probabilities = listOf(0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3).shuffled()
    val predictor = FileUsagePredictor(7, 2, 5)
    val validator = FileProbabilityValidator(listOf(0.9, 0.8), listOf(0.7, 0.6, 0.5, 0.4, 0.3))
    doTestWithPredefinedProbability(builder, 5, probabilities, predictor, validator)
  }
}

private class FileProbabilityValidator(val top: List<Double>, val rest: List<Double>) : TestStatisticsEventsValidator {
  override fun validateAll(events: List<LogEvent>) {
    val actualProbabilities = events.map { it.event.data["probability"] as Double }

    val actualTop = actualProbabilities.subList(0, top.size)
    CodeInsightFixtureTestCase.assertEquals("Top candidates probabilities is different from expected", top, actualTop)

    for (probability in actualProbabilities.subList(top.size, actualProbabilities.size)) {
      CodeInsightFixtureTestCase.assertTrue("Unknown probability in rest of candidates", rest.contains(probability))
    }
  }
}
