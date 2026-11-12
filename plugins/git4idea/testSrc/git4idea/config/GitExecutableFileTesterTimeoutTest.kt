// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.vcs.VcsException
import com.intellij.testFramework.junit5.TestApplication
import git4idea.i18n.GitBundle
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@TestApplication
@Suppress("SuspiciousPackagePrivateAccess")
internal class GitExecutableFileTesterTimeoutTest {
  private val executable = GitExecutable.Local("git")
  private val version = GitVersion.parse("git version 2.40.0")

  @Test
  fun `a request retries after a timeout and caches the success`() {
    val tester = StubTester { attempt -> if (attempt == 1) null else version }

    val timeout = tester.getResultFor(executable)
    assertThat(timeout.result).isNull()
    assertThat(timeout.exception)
      .isInstanceOf(GitVersionIdentificationException::class.java)
      .hasMessage(GitBundle.message("git.executable.validation.error.no.response.in.n.attempts.message", 1))
    assertThat(tester.getCachedResultFor(executable)).isNull()
    assertThat(tester.attempts).isEqualTo(1)

    val success = tester.getResultFor(executable)
    assertThat(success.result).isEqualTo(version)
    assertThat(success.exception).isNull()
    assertThat(tester.getCachedResultFor(executable)?.result).isEqualTo(version)
    assertThat(tester.attempts).isEqualTo(2)

    val cached = tester.getResultFor(executable)
    assertThat(cached.result).isEqualTo(version)
    assertThat(cached.exception).isNull()
    assertThat(tester.attempts).isEqualTo(2)
  }

  @Test
  fun `repeated timeouts remain uncached`() {
    val tester = StubTester { null }

    repeat(2) {
      val result = tester.getResultFor(executable)
      assertThat(result.result).isNull()
      assertThat(result.exception).isInstanceOf(GitVersionIdentificationException::class.java)
      assertThat(tester.getCachedResultFor(executable)).isNull()
    }
    assertThat(tester.attempts).isEqualTo(2)
  }

  @Test
  fun `an ordinary identification failure remains cached`() {
    val tester = StubTester { throw VcsException("Git error", null) }

    repeat(2) {
      val result = tester.getResultFor(executable)
      assertThat(result.result).isNull()
      assertThat(result.exception)
        .isInstanceOf(VcsException::class.java)
    }
    assertThat(tester.attempts).isEqualTo(1)
  }

  @Test
  fun `cancellation propagates and remains uncached`() {
    val cancellation = ProcessCanceledException()
    val tester = StubTester { throw cancellation }

    repeat(2) {
      assertThatThrownBy { tester.getResultFor(executable) }.isSameAs(cancellation)
      assertThat(tester.getCachedResultFor(executable)).isNull()
    }
    assertThat(tester.attempts).isEqualTo(2)
  }

  private class StubTester(private val outcome: (Int) -> GitVersion?) : GitExecutableFileTester() {
    var attempts = 0
      private set

    override fun runTestWithTimeout(executable: GitExecutable): GitVersion? {
      attempts++
      return outcome(attempts)
    }
  }
}
