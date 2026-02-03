package com.intellij.grazie.pro

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieConfig.State.Processing
import com.intellij.grazie.GrazieTestBase.Companion.maskSaxParserFactory
import com.intellij.grazie.cloud.license.GrazieLoginManager
import com.intellij.grazie.cloud.license.GrazieLoginState
import com.intellij.grazie.grammar.LanguageToolChecker
import com.intellij.grazie.grammar.LanguageToolChecker.TestChecker
import com.intellij.grazie.text.TextChecker
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach

@Suppress("JUnitTestCaseWithNoTests")
open class BaseTestCase {

  companion object {
    val TEST_CHECKER: TestChecker = TestChecker()
  }

  lateinit var myFixture: CodeInsightTestFixture
  val project: Project get() = myFixture.project
  val testRootDisposable: Disposable get() = myFixture.testRootDisposable

  @BeforeEach
  @Throws(Exception::class)
  fun setUpBaseCase() {
    myFixture = createFixture()
    initCloudProcessing()
    installLTTestChecker()
    maskSaxParserFactory(testRootDisposable)
  }

  @Throws(java.lang.Exception::class)
  private fun createFixture(): CodeInsightTestFixture {
    val factory = IdeaTestFixtureFactory.getFixtureFactory()
    val projectBuilder = factory.createLightFixtureBuilder(javaClass.getName())
    val fixture = factory.createCodeInsightFixture(projectBuilder.getFixture(), LightTempDirTestFixtureImpl(true))
    fixture.setUp()
    return fixture
  }

  @AfterEach
  @Throws(java.lang.Exception::class)
  fun tearDownBaseCase() {
    resetGrazieConfiguration()
    myFixture.tearDown()
  }

  fun installLTTestChecker() {
    val newExtensions = TextChecker.allCheckers()
      .map { if (it is LanguageToolChecker) TEST_CHECKER else it }
    ExtensionTestUtil.maskExtensions(ExtensionPointName("com.intellij.grazie.textChecker"), newExtensions, testRootDisposable)
  }

  fun initCloudProcessing() {
    runBlocking {
      GrazieLoginManager.getInstance().setState(GrazieLoginState.Cloud(GrazieTokenUtil.getTestToken()))
    }
    GrazieConfig.update { it.copy(explicitlyChosenProcessing = Processing.Cloud) }
  }

  fun runWithCloudProcessing(block: Runnable) {
    initCloudProcessing()
    try {
      block.run()
    }
    finally {
      resetGrazieConfiguration()
    }
  }

  fun initLocalProcessing() {
    runBlocking {
      GrazieLoginManager.getInstance().setState(GrazieLoginState.NoJba)
    }
  }

  protected fun configureCommitMessage(text: String) {
    myFixture.configureByText("a.txt", text)
    val commitMessage = CommitMessage(project)
    Disposer.register(testRootDisposable, commitMessage)
    myFixture.getEditor().getDocument().putUserData(CommitMessage.DATA_KEY, commitMessage)
  }

  fun resetGrazieConfiguration() {
    GrazieConfig.update { GrazieConfig.State() }
  }

  protected fun checkIntention(fileName: String, nameHint: String, before: String, after: String) {
    myFixture.configureByText(fileName, before)
    myFixture.launchAction(findSingleIntention(nameHint))
    myFixture.checkResult(after)
  }

  protected fun checkIntentionIsAbsent(fileName: String, nameHint: String?, text: String) {
    myFixture.configureByText(fileName, text)
    Assertions.assertFalse(
      availableIntentions.any { it.text == nameHint },
      "$nameHint should not be available"
    )
  }

  protected fun findSingleIntention(nameHint: String): IntentionAction {
    return ProgressManager.getInstance().runProcess<IntentionAction>({ myFixture.findSingleIntention(nameHint) }, ProgressIndicatorBase())
  }

  val availableIntentions: List<IntentionAction>
    get() {
      return ProgressManager.getInstance()
        .runProcess<List<IntentionAction>>({ myFixture.getAvailableIntentions() }, ProgressIndicatorBase())
    }
}

