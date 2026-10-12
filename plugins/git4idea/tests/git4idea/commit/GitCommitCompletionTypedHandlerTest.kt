// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.intellij.codeInsight.completion.CompletionPhase
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * The test has no Git repository, so `GitCommitCompletionContributor` adds no item.
 * The test therefore looks at the completion phase.
 * [CompletionPhase.EmptyAutoPopup] means that the session ran and found nothing.
 * [CompletionPhase.NoCompletion] means that no session started.
 */
@TestApplication
class GitCommitCompletionTypedHandlerTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private fun type(s: String) = CompletionAutoPopupTestUtil.type(myFixture, s)

  private fun runTest(block: () -> Unit) = CompletionAutoPopupTestUtil.runWithAutoPopupEnabled<Throwable>(myFixture, block)

  @Test
  fun `test the popup starts after the fixup command`(): Unit = runTest { assertPopupStarts("fixup") }

  @Test
  fun `test the popup starts after the squash command`(): Unit = runTest { assertPopupStarts("squash") }

  @Test
  fun `test the popup starts after the amend command`(): Unit = runTest { assertPopupStarts("amend") }

  @Test
  fun `test the popup ignores a partial command`(): Unit = runTest { assertPopupDoesNotStart("fixu") }

  @Test
  fun `test the popup ignores an other word`(): Unit = runTest { assertPopupDoesNotStart("hello") }

  @Test
  fun `test the popup ignores a command that is not at the start`(): Unit = runTest { assertPopupDoesNotStart("wip fixup") }

  private fun assertPopupStarts(message: String) {
    assertInstanceOf(CompletionPhase.EmptyAutoPopup::class.java, typeCommandEnd(message))
  }

  private fun assertPopupDoesNotStart(message: String) {
    assertSame(CompletionPhase.NoCompletion, typeCommandEnd(message))
  }

  private fun typeCommandEnd(message: String): CompletionPhase {
    myFixture.configureByText("commitMessage.txt", "$message<caret>")
    runInEdtAndWait {
      val commitMessage = CommitMessage(myFixture.project)
      Disposer.register(myFixture.testRootDisposable, commitMessage)
      myFixture.editor.document.putUserData(CommitMessage.DATA_KEY, commitMessage)
    }
    type("!")
    return runInEdtAndGet { CompletionServiceImpl.completionPhase }
  }
}
