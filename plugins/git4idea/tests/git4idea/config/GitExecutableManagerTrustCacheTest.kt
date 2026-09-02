// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.Disposable
import com.intellij.testFramework.TrustedProjectsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import git4idea.commands.GitCommandNotTrustedException
import git4idea.test.GitPlatformTestContext
import git4idea.test.gitPlatformContextFixture
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class GitExecutableManagerTrustCacheTest {

  private val contextFixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = contextFixture.get()

  @TestDisposable
  private lateinit var testDisposable: Disposable

  @Test
  fun `an untrusted-project failure is not cached and does not poison a later trusted call`(): Unit = with(context) {
    TrustedProjectsTestUtil.enableTrustedProjectsCheck(testDisposable)

    val manager = GitExecutableManager.getInstance()
    val executable = manager.getExecutable(project)

    // The fixture's own setup already tested this executable and cached a success for it;
    // drop that entry so the call below genuinely re-tests under the trust state set here.
    manager.dropVersionCache(executable)
    TrustedProjects.setProjectTrusted(project, false)
    val failure = assertThrows(GitVersionIdentificationException::class.java) {
      manager.identifyVersion(project, executable)
    }
    assertTrue(failure.cause is GitCommandNotTrustedException)

    // Without the fix, the poisoned cache entry (same GitExecutable, unchanged mtime)
    // would be replayed here even though the project is now trusted.
    TrustedProjects.setProjectTrusted(project, true)
    val version = manager.identifyVersion(project, executable)
    assertTrue(version.isSupported)
  }
}
