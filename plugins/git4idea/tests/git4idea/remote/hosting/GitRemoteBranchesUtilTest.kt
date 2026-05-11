// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.dvcs.repo.Repository
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.GitStandardRemoteBranch
import git4idea.branch.GitBranchesCollection
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.remote.hosting.GitRemoteBranchesUtil.findOrCreateRemote
import git4idea.repo.GitBranchTrackInfo
import git4idea.repo.GitHooksInfo
import git4idea.repo.GitRepoInfo
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.net.URI
import kotlin.jvm.java

class GitRemoteBranchesUtilTest {

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
    assertNotNull("Matching remote should be found", result)
    assertEquals("remote1", result?.name)
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
    assertNotNull("Matching remote should be found", result)
    assertEquals("remote1", result?.name)
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
    assertNull("No matching remote should be found", result)
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
    assertNull("No matching remote should be found", result)
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
    assertNull("No matching remote should be found", result)
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
    assertNotNull("Matching remote should be found", result)
    assertEquals("remote1", result?.name)
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
    assertNotNull("Matching remote should be found", result)
    assertEquals("remote1", result?.name)
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
    assertNotNull("Matching remote should be found", result)
    assertEquals("remote1", result?.name)
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
    assertNull("Matching remote should not be found", result)
  }

  @Test
  fun `test isRemoteBranchCheckedOut with matching remote branch`() {
    val mockRepository = mock(GitRepository::class.java)

    val remote = gitRemoteTest("origin", "https://example.com/org/repo.git")
    val trackedRemoteBranch = GitStandardRemoteBranch(remote, "main")
    val trackInfo = GitBranchTrackInfo(GitLocalBranch("main"), trackedRemoteBranch, false)

    val repositoryInfo = GitRepoInfo(
      GitLocalBranch("main"),
      "curRev",
      Repository.State.NORMAL,
      listOf(remote),
      emptyMap(),
      emptyMap(),
      listOf(trackInfo),
      emptyList(),
      GitHooksInfo(false, false),
      false
    )

    `when`(mockRepository.info).thenReturn(repositoryInfo)
    `when`(mockRepository.currentBranchName).thenReturn("main")
    `when`(mockRepository.branchTrackInfos).thenReturn(listOf(trackInfo))

    val result = GitRemoteBranchesUtil.isRemoteBranchCheckedOut(mockRepository, trackedRemoteBranch)
    assertTrue("Remote branch should be checked out", result)
  }

  @Test
  fun `test isRemoteBranchCheckedOut returns false when current branch doesn't match tracked local branch`() {
    val mockRepository = mock(GitRepository::class.java)

    val remote = gitRemoteTest("origin", "https://example.com/org/repo.git")
    val trackedRemoteBranch = GitStandardRemoteBranch(remote, "main")
    val trackInfo = GitBranchTrackInfo(GitLocalBranch("main"), trackedRemoteBranch, false)

    `when`(mockRepository.currentBranchName).thenReturn("feature")
    `when`(mockRepository.branchTrackInfos).thenReturn(listOf(trackInfo))

    val result = GitRemoteBranchesUtil.isRemoteBranchCheckedOut(mockRepository, trackedRemoteBranch)
    assertFalse("Remote branch should not be considered checked out", result)
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
    assertNotNull("Remote branch should be found", result)
  }

  @Test
  fun `test findOrCreateRemote returns existing remote and does not call addRemote`() = runBlocking {
    val mockRepository = mock(GitRepository::class.java)
    val mockRepositoryInfo = mock(GitRepoInfo::class.java)
    val mockGit = mock(Git::class.java)

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

    mockStatic(Git::class.java).use { gitStatic ->
      gitStatic.`when`<Git> { Git.getInstance() }.thenReturn(mockGit)

      val result = mockGit.findOrCreateRemote(mockRepository, hostedRemote)

      assertEquals("Should return existing remote", existingRemote, result)
      verify(mockGit, never()).addRemote(any(), anyString(), anyString())
      verify(mockRepository, never()).update()
    }
  }

  @Test
  fun `test findOrCreateRemote creates http remote when http is preferred`() = runBlocking {
    val mockRepository = mock(GitRepository::class.java)
    val mockRepositoryInfo = mock(GitRepoInfo::class.java)
    val mockGit = mock(Git::class.java)

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
    `when`(mockGit.addRemote(eq(mockRepository), anyString(), anyString())).thenAnswer { invocation ->
      val name = invocation.getArgument<String>(1)
      val url = invocation.getArgument<String>(2)
      remotesState += gitRemoteTest(name, url)
      mock(GitCommandResult::class.java)
    }

    mockStatic(Git::class.java).use { gitStatic ->
      gitStatic.`when`<Git> { Git.getInstance() }.thenReturn(mockGit)

      val result = mockGit.findOrCreateRemote(mockRepository, hostedRemote)

      assertNotNull("Remote should be created", result)
      assertEquals("upstream", result?.name)
      verify(mockGit).addRemote(mockRepository, "upstream", "https://example.com/org/repo.git")
      verify(mockRepository).update()
    }
  }

  @Test
  fun `test findOrCreateRemote creates ssh remote when http is not preferred`() = runBlocking {
    val mockRepository = mock(GitRepository::class.java)
    val mockRepositoryInfo = mock(GitRepoInfo::class.java)
    val mockGit = mock(Git::class.java)

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
    `when`(mockGit.addRemote(eq(mockRepository), anyString(), anyString())).thenAnswer { invocation ->
      val name = invocation.getArgument<String>(1)
      val url = invocation.getArgument<String>(2)
      remotesState += gitRemoteTest(name, url)
      mock(GitCommandResult::class.java)
    }

    mockStatic(Git::class.java).use { gitStatic ->
      gitStatic.`when`<Git> { Git.getInstance() }.thenReturn(mockGit)

      val result = mockGit.findOrCreateRemote(mockRepository, hostedRemote)

      assertNotNull("Remote should be created", result)
      assertEquals("upstream", result?.name)
      verify(mockGit).addRemote(mockRepository, "upstream", "git@example.com:org/repo.git")
      verify(mockRepository).update()
    }
  }

  @Test
  fun `test findOrCreateRemote returns null when no url is available`() = runBlocking {
    val mockRepository = mock(GitRepository::class.java)
    val mockRepositoryInfo = mock(GitRepoInfo::class.java)
    val mockGit = mock(Git::class.java)

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

    mockStatic(Git::class.java).use { gitStatic ->
      gitStatic.`when`<Git> { Git.getInstance() }.thenReturn(mockGit)

      val result = mockGit.findOrCreateRemote(mockRepository, hostedRemote)

      assertNull("Should return null when there is no URL to create remote", result)
      verify(mockGit, never()).addRemote(any(), anyString(), anyString())
      verify(mockRepository, never()).update()
    }
  }

  private fun gitRemoteTest(name: String, element: String): GitRemote =
    GitRemote(name = name, urls = listOf(element), pushUrls = listOf(element), fetchRefSpecs = listOf(), pushRefSpecs = listOf())
}