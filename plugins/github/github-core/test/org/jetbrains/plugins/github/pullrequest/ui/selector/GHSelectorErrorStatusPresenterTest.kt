// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.selector

import com.intellij.testFramework.junit5.TestApplication
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorViewModel.Error.SubmissionError
import io.mockk.mockk
import org.jetbrains.plugins.github.authentication.GHLoginSource
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.exceptions.GithubRateLimitExceededException
import org.jetbrains.plugins.github.exceptions.GithubSecondaryRateLimitExceededException
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * A new login can't help with a rate limit, so the error of the selector doesn't offer it.
 */
@TestApplication
internal class GHSelectorErrorStatusPresenterTest {
  private val presenter = GHSelectorErrorStatusPresenter(mockk(), GHLoginSource.UNKNOWN)

  @Test
  fun `a primary rate limit error offers no login`() {
    val error = submissionError(GithubRateLimitExceededException("rate limit exceeded", Clock.System.now()))

    assertNull(presenter.getErrorAction(error))
  }

  @Test
  fun `a secondary rate limit error offers no login`() {
    val error = submissionError(GithubSecondaryRateLimitExceededException("secondary rate limit exceeded", 1.minutes))

    assertNull(presenter.getErrorAction(error))
  }

  @Test
  fun `an authentication error offers a login`() {
    val error = submissionError(GithubAuthenticationException("bad credentials"))

    assertNotNull(presenter.getErrorAction(error))
  }

  private fun submissionError(exception: Throwable): SubmissionError = SubmissionError(mockk(), GithubAccount("test"), exception)
}
