// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitWorkingTree
import git4idea.ignore.findOrCreateDir
import git4idea.test.MockGitRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.concurrent.atomic.AtomicReference

@TestApplication
internal class GitWorktreeUpdateListenerTest {
  private val projectFixture = projectFixture()
  private val project get() = projectFixture.get()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `test repositoryUpdated triggers update for matching worktree`() {
    val root = createDir("root")
    val mockRepository = MockGitRepository(project, root)

    val secondWorktreeRoot = createDir("secondWorktree")
    mockRepository.workingTrees += GitWorkingTree(
      VcsUtil.getFilePath(secondWorktreeRoot),
      null,
      false,
      false
    )

    mockGitRepositoryManager(mockRepository) {
      val updatedRepository = AtomicReference<GitRepository?>(null)
      val listener = GitWorktreeUpdateListener(project) { repo ->
        updatedRepository.set(repo)
      }

      val differentProject = mock<Project>()
      listener.repositoryUpdated(differentProject, secondWorktreeRoot)

      assertEquals(mockRepository, updatedRepository.get())
    }
  }

  @Test
  fun `test repositoryUpdated does not trigger for same project`() {
    val root = createDir("root")
    val mockRepository = MockGitRepository(project, root)
    mockRepository.workingTrees += GitWorkingTree(
      VcsUtil.getFilePath(createDir("secondWorktree")),
      null,
      false,
      false
    )

    mockGitRepositoryManager(mockRepository) {
      val updatedRepository = AtomicReference<GitRepository?>(null)
      val listener = GitWorktreeUpdateListener(project) { repo ->
        updatedRepository.set(repo)
      }

      listener.repositoryUpdated(project, root)

      assertNull(updatedRepository.get(), "Repository should NOT have been updated for same project")
    }
  }

  @Test
  fun `test repositoryUpdated does not trigger for single worktree`() {
    val root = createDir("root")
    val mockRepository = MockGitRepository(project, root)

    mockGitRepositoryManager(mockRepository) {
      val updatedRepository = AtomicReference<GitRepository?>(null)
      val listener = GitWorktreeUpdateListener(project) { repo ->
        updatedRepository.set(repo)
      }

      val differentProject = mock<Project>()
      listener.repositoryUpdated(differentProject, root)

      assertNull(updatedRepository.get())
    }
  }

  @Test
  fun `test repositoryUpdated does not trigger for non-matching root`() {
    val root = createDir("root")
    val mockRepository = MockGitRepository(project, root)

    mockRepository.workingTrees += GitWorkingTree(
      VcsUtil.getFilePath(createDir("secondWorktree")),
      null,
      false,
      false
    )

    mockGitRepositoryManager(mockRepository) {
      val updatedRepository = AtomicReference<GitRepository?>(null)
      val listener = GitWorktreeUpdateListener(project) { repo ->
        updatedRepository.set(repo)
      }

      val differentProject = mock<Project>()
      val nonMatchingRoot = createDir("nonMatchingRoot")
      listener.repositoryUpdated(differentProject, nonMatchingRoot)

      assertNull(updatedRepository.get())
    }
  }

  @Test
  fun `test repositoryUpdated triggers for matching worktree when first repository has single worktree`() {
    val rootA = createDir("rootA")
    val repoA = MockGitRepository(project, rootA)
    // repoA has single worktree (size 1)

    val rootB = createDir("rootB")
    val repoB = MockGitRepository(project, rootB)
    val secondWorktreeRootB = createDir("secondWorktreeB")
    repoB.workingTrees += GitWorkingTree(
      VcsUtil.getFilePath(secondWorktreeRootB),
      null,
      false,
      false
    )

    mockGitRepositoryManager(repoA, repoB) {
      val updatedRepositories = mutableListOf<GitRepository>()
      val listener = GitWorktreeUpdateListener(project) { repo ->
        updatedRepositories.add(repo)
      }

      val differentProject = mock<Project>()
      listener.repositoryUpdated(differentProject, secondWorktreeRootB)

      assertThat(updatedRepositories).contains(repoB)
      assertThat(updatedRepositories).doesNotContain(repoA)
    }
  }

  private fun createDir(name: String): VirtualFile = WriteAction.computeAndWait<VirtualFile, Throwable> { project.baseDir.findOrCreateDir(name) }

  private fun mockGitRepositoryManager(vararg repositories: GitRepository, action: () -> Unit) {
    val mockRepositoryManager = mock<GitRepositoryManager>()
    `when`(mockRepositoryManager.repositories).thenReturn(repositories.toList())
    project.replaceService(GitRepositoryManager::class.java, mockRepositoryManager, disposable)
    action()
    Mockito.reset(mockRepositoryManager)
  }
}
