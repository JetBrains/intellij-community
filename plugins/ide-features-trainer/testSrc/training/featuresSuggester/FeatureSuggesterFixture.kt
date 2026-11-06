// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.featuresSuggester

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import training.featuresSuggester.settings.FeatureSuggesterSettings
import training.featuresSuggester.suggesters.FeatureSuggester

/**
 * Creates a [FeatureSuggesterFixture] for the suggester with the [suggesterId] identifier.
 * Declare the result as an instance property, so that each test gets a new project.
 *
 * The setup configures the editor with [codeFileName] and waits for the indexes.
 * Then it enables all suggesters and records each suggestion of the project.
 *
 * @param testDataPath the absolute path of the directory with [codeFileName]
 * @param codeFileName the name of the file that each test edits
 * @param suggesterId the expected [PopupSuggestion.suggesterId] of [FeatureSuggesterFixture.assertSuggestedCorrectly]
 */
fun featureSuggesterFixture(
  testDataPath: String,
  codeFileName: String,
  suggesterId: String,
): TestFixture<FeatureSuggesterFixture> = testFixture("featureSuggesterFixture") {
  val myFixture = codeInsightFixture(codeInsightProjectFixture()).init()
  myFixture.testDataPath = testDataPath
  val result = FeatureSuggesterFixture(myFixture, suggesterId)
  withContext(Dispatchers.EDT) {
    myFixture.configureByFile(codeFileName)
  }
  IndexingTestUtil.waitUntilIndexesAreReady(myFixture.project)
  SuggestingUtils.forceShowSuggestions = true
  val settings = FeatureSuggesterSettings.instance()
  FeatureSuggester.suggesters.forEach { settings.setEnabled(it.id, true) }
  val disposable = Disposer.newDisposable()
  FeatureSuggesterTestUtils.subscribeToSuggestions(myFixture.project, disposable) { suggestion -> result.expectedSuggestion = suggestion }
  initialized(result) {
    Disposer.dispose(disposable)
  }
}

/**
 * The editor of a feature suggester test and the last suggestion that the project shows.
 */
class FeatureSuggesterFixture internal constructor(
  val myFixture: CodeInsightTestFixture,
  private val suggesterId: String,
) {
  val project: Project get() = myFixture.project

  var expectedSuggestion: Suggestion = NoSuggestion
    internal set

  /**
   * Runs [body] on the EDT, then runs the pending events of the EDT.
   * Thus, the checks that [body] schedules with [FeatureSuggesterTestUtils.testInvokeLater] run before the end of the test.
   */
  fun runTest(body: FeatureSuggesterFixture.() -> Unit): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      body()
      yield()
    }
  }

  fun assertSuggestedCorrectly() {
    assertTrue(expectedSuggestion is PopupSuggestion, expectedSuggestion.javaClass.name)
    assertEquals(suggesterId, (expectedSuggestion as PopupSuggestion).suggesterId)
  }
}
