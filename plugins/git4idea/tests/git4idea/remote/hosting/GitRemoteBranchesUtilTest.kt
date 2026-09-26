// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.GitStandardRemoteBranch
import git4idea.branch.GitBranchesCollection
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.push.GitSpecialRefRemoteBranch
import git4idea.repo.GitBranchTrackInfo
import git4idea.repo.GitRemote
import git4idea.repo.GitRepoInfo
import git4idea.repo.GitRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.net.URI

private const val SPECIAL_REF = "refs/pull/42/head"
private const val CURRENT_REVISION = "1234567890abcdef1234567890abcdef12345678"
private const val OTHER_REVISION = "fedcba0987654321fedcba0987654321fedcba09"

@TestApplication
class GitRemoteBranchesUtilTest {

  @TestDisposable
  private lateinit var testDisposable: Disposable

  @Test
  fun `test findRemote with matching remote`() {
    val mockRepository = mock(GitRepository::class.java)
    val mockRepositoryInfo = mock(GitRepoInfo::class.java)
    val remote1 = gitRemoteTest("remote1", "https://example.com/org/repo.git")
    val remote2 = gitRemoteTest("remote2", "https://example.com/org/repo2.git")

    `when`(mockRepository.info).thenReturn(mockRepositoryInfo)
    `when`(mockRepositoryInfo.remotes).thenReturn(listOf(remote1, remote2))

    val hostedRemote = HostedGitRepositoryRemote(
      name = "origin",
      serverUri = URI("https://example.com/org/repo"),
      path = "org/repo",
      httpUrl = "https://example.com/org/repo.git",
      sshUrl = "git@example.com:org/repo.git"
    )

    val result = GitRemoteBranchesUtil.findRemote(mockRepository, hostedRemote)
    assertThat(result?.name).describedAs("Matching remote should be found").isEqualTo("remote1")
  }

  @Test
  fun `test findRemote with matching remote using ssh`() {
    val mockRepository = mock(GitRepository::class.java)
    val mockRepositoryInfo = mock(GitRepoInfo::class.java)
    val remote1 = gitRemoteTest("remote1", "git@example.com:org/repo.git")
    val remote2 = gitRemoteTest("remote2", "git@example.com:org/repo2.git")

    `when`(mockRepository.info).thenReturn(mockRepositoryInfo)
    `when`(mockRepositoryInfo.remotes).thenReturn(listOf(remote1, remote2))

    val hostedRemote = HostedGitRepositoryRemote(
      name = "origin",
      serverUri = URI("https://example.com/org/repo"),
      path = "org/repo",
      httpUrl = "https://example.com/org/repo.git",
      sshUrl = "git@example.com:org/repo.git"
    )

    val result = GitRemoteBranchesUtil.findRemote(mockRepository, hostedRemote)
    assertThat(result?.name).describedAs("Matching remote should be found").isEqualTo("remote1")
  }

  @Test
  fun `test findRemote with no matching remote`() {
    val mockRepository = mock(GitRepository::class.java)
    val mockRepositoryInfo = mock(GitRepoInfo::class.java)
    val remote1 = gitRemoteTest("remote1", "https://example.com/org/repo.git")
    val remote2 = gitRemoteTest("remote2", "https://example.com/org/repo2.git")

    `when`(mockRepository.info).thenReturn(mockRepositoryInfo)
    `when`(mockRepositoryInfo.remotes).thenReturn(listOf(remote1, remote2))

    val hostedRemote = HostedGitRepositoryRemote(
      name = "origin",
      serverUri = URI("https://example.com/org/repoOther"),
      path = "org/repoOther",
      httpUrl = "https://example.com/org/repoOther.git",
      sshUrl = "git@example.com:org/repoOther.git"
    )

    val result = GitRemoteBranchesUtil.findRemote(mockRepository, hostedRemote)
    assertThat(result).describedAs("No matching remote should be found").isNull()
  }

  @Test
  fun `test findRemote with no matching remote when the target path is a substring of firstUrl`() {
    val mockRepository = mock(GitRepository::class.java)
    val mockRepositoryInfo = mock(GitRepoInfo::class.java)
    val remote1 = gitRemoteTest("remote1", "https://example.com/org/repoSuffix.git")
    val remote2 = gitRemoteTest("remote2", "https://example.com/org/repoSuffix2.git")

    `when`(mockRepository.info).thenReturn(mockRepositoryInfo)
    `when`(mockRepositoryInfo.remotes).thenReturn(listOf(remote1, remote2))

    val hostedRemote = HostedGitRepositoryRemote(
      name = "origin",
      serverUri = URI("https://example.com"),
      path = "org/repo",
      httpUrl = "https://example.com/org/repo.git",
      sshUrl = "git@example.com:org/repo.git"
    )

    val result = GitRemoteBranchesUtil.findRemote(mockRepository, hostedRemote)
    assertThat(result).describedAs("No matching remote should be found").isNull()
  }

  @Test
  fun `test findRemote with no matching remote when the firstUrl path is a substring of the target path`() {
    val mockRepository = mock(GitRepository::class.java)
    val mockRepositoryInfo = mock(GitRepoInfo::class.java)
    val remote1 = gitRemoteTest("remote1", "https://example.com/org/repo.git")
    val remote2 = gitRemoteTest("remote2", "https://example.com/org/repo2.git")

    `when`(mockRepository.info).thenReturn(mockRepositoryInfo)
    `when`(mockRepositoryInfo.remotes).thenReturn(listOf(remote1, remote2))

    val hostedRemote = HostedGitRepositoryRemote(
      name = "origin",
      serverUri = URI("https://example.com"),
      path = "org/repoOther",
      httpUrl = "https://example.com/org/repoOther.git",
      sshUrl = "git@example.com:org/repoOther.git"
    )

    val result = GitRemoteBranchesUtil.findRemote(mockRepository, hostedRemote)
    assertThat(result).describedAs("No matching remote should be found").isNull()
  }

  @Test
  fun `test findRemote by shorten serverUri and equal path`() {
    val mockRepository = mock(GitRepository::class.java)
    val mockRepositoryInfo = mock(GitRepoInfo::class.java)
    val remote1 = gitRemoteTest("remote1", "https://example.com/org/repo.git")
    val remote2 = gitRemoteTest("remote2", "https://example.com/org/repo2.git")

    `when`(mockRepository.info).thenReturn(mockRepositoryInfo)
    `when`(mockRepositoryInfo.remotes).thenReturn(listOf(remote1, remote2))

    val hostedRemote = HostedGitRepositoryRemote(
      name = "origin",
      serverUri = URI("https://example.com"),
      path = "org/repo",
      httpUrl = "https://example.com/org/repo.git",
      sshUrl = "git@example.com:org/repo.git"
    )

    val result = GitRemoteBranchesUtil.findRemote(mockRepository, hostedRemote)
    assertThat(result?.name).describedAs("Matching remote should be found").isEqualTo("remote1")
  }

  @Test
  fun `test findRemote by aliased serverUri and equal path`() {
    val mockRepository = mock(GitRepository::class.java)
    val mockRepositoryInfo = mock(GitRepoInfo::class.java)
    val remote1 = gitRemoteTest("remote1", "https://aliased/org/repo.git")
    val remote2 = gitRemoteTest("remote2", "https://aliased/org/repo2.git")

    `when`(mockRepository.info).thenReturn(mockRepositoryInfo)
    `when`(mockRepositoryInfo.remotes).thenReturn(listOf(remote1, remote2))

    val hostedRemote = HostedGitRepositoryRemote(
      name = "origin",
      serverUri = URI("https://aliased"),
      path = "org/repo",
      httpUrl = "https://example.com/org/repo.git",
      sshUrl = "git@example.com:org/repo.git"
    )

    val result = GitRemoteBranchesUtil.findRemote(mockRepository, hostedRemote)
    assertThat(result?.name).describedAs("Matching remote should be found").isEqualTo("remote1")
  }

  @Test
  fun `test findRemote returns a match if firstUrl host matches the real host, not the alias`() {
    val mockRepository = mock(GitRepository::class.java)
    val mockRepositoryInfo = mock(GitRepoInfo::class.java)
    val remote1 = gitRemoteTest("remote1", "https://example.com/org/repo.git")
    val remote2 = gitRemoteTest("remote2", "https://example.com/org/repo2.git")

    `when`(mockRepository.info).thenReturn(mockRepositoryInfo)
    `when`(mockRepositoryInfo.remotes).thenReturn(listOf(remote1, remote2))

    val hostedRemote = HostedGitRepositoryRemote(
      name = "origin",
      serverUri = URI("https://aliased"),
      path = "org/repo",
      httpUrl = "https://example.com/org/repo.git",
      sshUrl = "git@example.com:org/repo.git"
    )

    val result = GitRemoteBranchesUtil.findRemote(mockRepository, hostedRemote)
    assertThat(result?.name).describedAs("Matching remote should be found").isEqualTo("remote1")
  }

  @Test
  fun `test findRemote returns no matches if firstUrl host doesn't match neither the real one nor the alias`() {
    val mockRepository = mock(GitRepository::class.java)
    val mockRepositoryInfo = mock(GitRepoInfo::class.java)
    val remote1 = gitRemoteTest("remote1", "https://otherpath/org/repo.git")
    val remote2 = gitRemoteTest("remote2", "https://otherpath/org/repo2.git")

    `when`(mockRepository.info).thenReturn(mockRepositoryInfo)
    `when`(mockRepositoryInfo.remotes).thenReturn(listOf(remote1, remote2))

    val hostedRemote = HostedGitRepositoryRemote(
      name = "origin",
      serverUri = URI("https://aliased"),
      path = "org/repo",
      httpUrl = "https://example.com/org/repo.git",
      sshUrl = "git@example.com:org/repo.git"
    )

    val result = GitRemoteBranchesUtil.findRemote(mockRepository, hostedRemote)
    assertThat(result).describedAs("Matching remote should not be found").isNull()
  }

  @Test
  @Timeout(30)
  fun `test testRemoteBranchCheckedOut with matching remote branch`(): Unit = timeoutRunBlocking {
    val mockRepository = mock(GitRepository::class.java)

    val remote = gitRemoteTest("origin", "https://example.com/org/repo.git")
    val trackedRemoteBranch = GitStandardRemoteBranch(remote, "main")
    val trackInfo = GitBranchTrackInfo(GitLocalBranch("main"), trackedRemoteBranch, false)

    `when`(mockRepository.currentBranchName).thenReturn("main")
    `when`(mockRepository.branchTrackInfos).thenReturn(listOf(trackInfo))

    val result = GitRemoteBranchesUtil.testRemoteBranchCheckedOut(mockRepository, trackedRemoteBranch)
    assertThat(result).describedAs("Remote branch should be checked out").isTrue()
  }

  @Test
  @Timeout(30)
  fun `test testRemoteBranchCheckedOut returns false when current branch doesn't match tracked local branch`(): Unit = timeoutRunBlocking {
    val mockRepository = mock(GitRepository::class.java)

    val remote = gitRemoteTest("origin", "https://example.com/org/repo.git")
    val trackedRemoteBranch = GitStandardRemoteBranch(remote, "main")
    val trackInfo = GitBranchTrackInfo(GitLocalBranch("main"), trackedRemoteBranch, false)

    `when`(mockRepository.currentBranchName).thenReturn("feature")
    `when`(mockRepository.branchTrackInfos).thenReturn(listOf(trackInfo))

    val result = GitRemoteBranchesUtil.testRemoteBranchCheckedOut(mockRepository, trackedRemoteBranch)
    assertThat(result).describedAs("Remote branch should not be considered checked out").isFalse()
  }

  @Test
  @Timeout(30)
  fun `test testRemoteBranchCheckedOut with special ref resolved to the current revision`(): Unit = timeoutRunBlocking {
    val mockRepository = mock(GitRepository::class.java)
    val specialRefBranch = GitSpecialRefRemoteBranch(SPECIAL_REF, gitRemoteTest("origin", "https://example.com/org/repo.git"))

    `when`(mockRepository.currentRevision).thenReturn(CURRENT_REVISION)
    registerGitMock {
      `when`(it.resolveReference(mockRepository, SPECIAL_REF)).thenReturn(HashImpl.build(CURRENT_REVISION))
    }

    val result = GitRemoteBranchesUtil.testRemoteBranchCheckedOut(mockRepository, specialRefBranch)
    assertThat(result).describedAs("Special ref resolved to the current revision should be checked out").isTrue()
  }

  @Test
  @Timeout(30)
  fun `test testRemoteBranchCheckedOut with special ref resolved to another revision`(): Unit = timeoutRunBlocking {
    val mockRepository = mock(GitRepository::class.java)
    val specialRefBranch = GitSpecialRefRemoteBranch(SPECIAL_REF, gitRemoteTest("origin", "https://example.com/org/repo.git"))

    `when`(mockRepository.currentRevision).thenReturn(CURRENT_REVISION)
    val mockGit = registerGitMock {
      `when`(it.resolveReference(mockRepository, SPECIAL_REF)).thenReturn(HashImpl.build(OTHER_REVISION))
    }

    val result = GitRemoteBranchesUtil.testRemoteBranchCheckedOut(mockRepository, specialRefBranch)
    assertThat(result).describedAs("Special ref resolved to another revision should not be checked out").isFalse()
    verify(mockGit).resolveReference(mockRepository, SPECIAL_REF)
  }

  @Test
  @Timeout(30)
  fun `test testRemoteBranchCheckedOut with unresolvable special ref`(): Unit = timeoutRunBlocking {
    val mockRepository = mock(GitRepository::class.java)
    val specialRefBranch = GitSpecialRefRemoteBranch(SPECIAL_REF, gitRemoteTest("origin", "https://example.com/org/repo.git"))

    `when`(mockRepository.currentRevision).thenReturn(CURRENT_REVISION)
    val mockGit = registerGitMock {
      `when`(it.resolveReference(mockRepository, SPECIAL_REF)).thenReturn(null)
    }

    val result = GitRemoteBranchesUtil.testRemoteBranchCheckedOut(mockRepository, specialRefBranch)
    assertThat(result).describedAs("Unresolvable special ref should not be checked out").isFalse()
    verify(mockGit).resolveReference(mockRepository, SPECIAL_REF)
  }

  @Test
  fun `test findRemoteBranch`() {
    val mockRepositoryInfo = mock(GitRepoInfo::class.java)
    val mockRemote = mock(GitRemote::class.java)
    val mockBranches = mock(GitBranchesCollection::class.java)

    `when`(mockRemote.firstUrl).thenReturn("https://example.com/org/repo.git")
    `when`(mockRepositoryInfo.remotes).thenReturn(listOf(mockRemote))
    `when`(mockBranches.findRemoteBranch("main")).thenReturn(mock(GitRemoteBranch::class.java))

    val hostedRemote = HostedGitRepositoryRemote(
      name = "origin",
      serverUri = URI("https://example.com"),
      path = "org/repo",
      httpUrl = "https://example.com/org/repo.git",
      sshUrl = "git@example.com:org/repo.git"
    )

    val hostedBranch = HostedGitRepositoryRemoteBranch(
      remote = hostedRemote,
      branchName = "main"
    )

    val result = GitRemoteBranchesUtil.findRemoteBranch(mockRepositoryInfo, hostedBranch)
    assertThat(result).describedAs("Remote branch should be found").isNotNull()
  }

  @Test
  @Timeout(30)
  fun `test findOrCreateRemote returns existing remote and does not call addRemote`(): Unit = timeoutRunBlocking {
    val mockRepository = mock(GitRepository::class.java)
    val mockRepositoryInfo = mock(GitRepoInfo::class.java)

    val existingRemote = gitRemoteTest("origin", "https://example.com/org/repo.git")

    val hostedRemote = HostedGitRepositoryRemote(
      name = "origin",
      serverUri = URI("https://example.com"),
      path = "org/repo",
      httpUrl = "https://example.com/org/repo.git",
      sshUrl = "git@example.com:org/repo.git"
    )

    `when`(mockRepository.info).thenReturn(mockRepositoryInfo)
    `when`(mockRepositoryInfo.remotes).thenReturn(listOf(existingRemote))
    `when`(mockRepository.remotes).thenReturn(listOf(existingRemote))

    val mockGit = registerGitMock()

    val result = GitRemoteBranchesUtil.findOrCreateRemote(mockRepository, hostedRemote)

    assertThat(result).describedAs("Should return existing remote").isEqualTo(existingRemote)
    verify(mockGit, never()).addRemote(any(), anyString(), anyString())
    verify(mockRepository, never()).update()
  }

  @Test
  @Timeout(30)
  fun `test findOrCreateRemote creates http remote when http is preferred`(): Unit = timeoutRunBlocking {
    val mockRepository = mock(GitRepository::class.java)
    val mockRepositoryInfo = mock(GitRepoInfo::class.java)

    val originHttpRemote = gitRemoteTest("origin", "https://example.com/another/repo.git")

    val hostedRemote = HostedGitRepositoryRemote(
      name = "upstream",
      serverUri = URI("https://example.com"),
      path = "org/repo",
      httpUrl = "https://example.com/org/repo.git",
      sshUrl = "git@example.com:org/repo.git"
    )

    `when`(mockRepository.info).thenReturn(mockRepositoryInfo)
    `when`(mockRepositoryInfo.remotes).thenReturn(emptyList())
    val remotesState = mutableListOf<GitRemote>()
    remotesState.add(originHttpRemote)
    `when`(mockRepository.remotes).thenAnswer { remotesState.toList() }

    val mockGit = registerGitMock { git ->
      `when`(git.addRemote(eq(mockRepository), anyString(), anyString())).thenAnswer { invocation ->
        val name = invocation.getArgument<String>(1)
        val url = invocation.getArgument<String>(2)
        remotesState += gitRemoteTest(name, url)
        mock(GitCommandResult::class.java)
      }
    }

    val result = GitRemoteBranchesUtil.findOrCreateRemote(mockRepository, hostedRemote)

    assertThat(result?.name).describedAs("Remote should be created").isEqualTo("upstream")
    verify(mockGit).addRemote(mockRepository, "upstream", "https://example.com/org/repo.git")
    verify(mockRepository).update()
  }

  @Test
  @Timeout(30)
  fun `test findOrCreateRemote creates ssh remote when http is not preferred`(): Unit = timeoutRunBlocking {
    val mockRepository = mock(GitRepository::class.java)
    val mockRepositoryInfo = mock(GitRepoInfo::class.java)

    val originSshRemote = gitRemoteTest("origin", "git@example.com:another/repo.git")

    val hostedRemote = HostedGitRepositoryRemote(
      name = "upstream",
      serverUri = URI("https://example.com"),
      path = "org/repo",
      httpUrl = "https://example.com/org/repo.git",
      sshUrl = "git@example.com:org/repo.git"
    )

    `when`(mockRepository.info).thenReturn(mockRepositoryInfo)

    val remotesState = mutableListOf<GitRemote>()
    remotesState.add(originSshRemote)
    `when`(mockRepository.remotes).thenAnswer { remotesState.toList() }

    val mockGit = registerGitMock { git ->
      `when`(git.addRemote(eq(mockRepository), anyString(), anyString())).thenAnswer { invocation ->
        val name = invocation.getArgument<String>(1)
        val url = invocation.getArgument<String>(2)
        remotesState += gitRemoteTest(name, url)
        mock(GitCommandResult::class.java)
      }
    }

    val result = GitRemoteBranchesUtil.findOrCreateRemote(mockRepository, hostedRemote)

    assertThat(result?.name).describedAs("Remote should be created").isEqualTo("upstream")
    verify(mockGit).addRemote(mockRepository, "upstream", "git@example.com:org/repo.git")
    verify(mockRepository).update()
  }

  @Test
  @Timeout(30)
  fun `test findOrCreateRemote returns null when no url is available`(): Unit = timeoutRunBlocking {
    val mockRepository = mock(GitRepository::class.java)
    val mockRepositoryInfo = mock(GitRepoInfo::class.java)

    val hostedRemote = HostedGitRepositoryRemote(
      name = "upstream",
      serverUri = URI("https://example.com"),
      path = "org/repo",
      httpUrl = null,
      sshUrl = null
    )

    `when`(mockRepository.info).thenReturn(mockRepositoryInfo)
    `when`(mockRepositoryInfo.remotes).thenReturn(emptyList())
    `when`(mockRepository.remotes).thenReturn(emptyList())

    val mockGit = registerGitMock()

    val result = GitRemoteBranchesUtil.findOrCreateRemote(mockRepository, hostedRemote)

    assertThat(result).describedAs("Should return null when there is no URL to create remote").isNull()
    verify(mockGit, never()).addRemote(any(), anyString(), anyString())
    verify(mockRepository, never()).update()
  }

  private fun gitRemoteTest(name: String, element: String): GitRemote =
    GitRemote(name = name, urls = listOf(element), pushUrls = listOf(element), fetchRefSpecs = listOf(), pushRefSpecs = listOf())

  /**
   * [GitRemoteBranchesUtil] reaches for [Git] via [Git.getInstance] on a background dispatcher, so the mock has to be registered as an
   * application service: a Mockito static mock is confined to the thread that created it and would not be visible there.
   */
  private fun registerGitMock(setUp: (Git) -> Unit = {}): Git {
    val mockGit = mock(Git::class.java)
    setUp(mockGit)
    ApplicationManager.getApplication().registerOrReplaceServiceInstance(Git::class.java, mockGit, testDisposable)
    return mockGit
  }
}
