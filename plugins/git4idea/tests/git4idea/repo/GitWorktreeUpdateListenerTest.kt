// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitWorkingTree
import git4idea.ignore.findOrCreateDir
import git4idea.test.MockGitRepository
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.concurrent.atomic.AtomicReference

internal class GitWorktreeUpdateListenerTest : LightPlatformTestCase() {
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

      assertNull("Repository should NOT have been updated for same project", updatedRepository.get())
    }
  }

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

      assertContainsElements(updatedRepositories, repoB)
      assertDoesntContain(updatedRepositories, repoA)
    }
  }

  private fun createDir(name: String): VirtualFile = application.runWriteAction(Computable { project.baseDir.findOrCreateDir(name) })

  private fun mockGitRepositoryManager(vararg repositories: GitRepository, action: () -> Unit) {
    val mockRepositoryManager = mock<GitRepositoryManager>()
    `when`(mockRepositoryManager.repositories).thenReturn(repositories.toList())
    project.replaceService(GitRepositoryManager::class.java, mockRepositoryManager, testRootDisposable)
    action()
    Mockito.reset(mockRepositoryManager)
  }
}
