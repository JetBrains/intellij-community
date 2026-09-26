// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.intellij.codeInsight.completion.CompletionPhase
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestCase
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait

/**
 * The test has no Git repository, so `GitCommitCompletionContributor` adds no item.
 * The test therefore looks at the completion phase.
 * [CompletionPhase.EmptyAutoPopup] means that the session ran and found nothing.
 * [CompletionPhase.NoCompletion] means that no session started.
 */
class GitCommitCompletionTypedHandlerTest : CompletionAutoPopupTestCase() {
  fun `test the popup starts after the fixup command`() = assertPopupStarts("fixup")

  fun `test the popup starts after the squash command`() = assertPopupStarts("squash")

  fun `test the popup starts after the amend command`() = assertPopupStarts("amend")

  fun `test the popup ignores a partial command`() = assertPopupDoesNotStart("fixu")

  fun `test the popup ignores an other word`() = assertPopupDoesNotStart("hello")

  fun `test the popup ignores a command that is not at the start`() = assertPopupDoesNotStart("wip fixup")

  private fun assertPopupStarts(message: String) {
    assertInstanceOf(typeCommandEnd(message), CompletionPhase.EmptyAutoPopup::class.java)
  }

  private fun assertPopupDoesNotStart(message: String) {
    assertSame(CompletionPhase.NoCompletion, typeCommandEnd(message))
  }

  private fun typeCommandEnd(message: String): CompletionPhase {
    myFixture.configureByText("commitMessage.txt", "$message<caret>")
    runInEdtAndWait {
      val commitMessage = CommitMessage(project)
      Disposer.register(myFixture.testRootDisposable, commitMessage)
      myFixture.editor.document.putUserData(CommitMessage.DATA_KEY, commitMessage)
    }
    type("!")
    return runInEdtAndGet { CompletionServiceImpl.completionPhase }
  }
}
