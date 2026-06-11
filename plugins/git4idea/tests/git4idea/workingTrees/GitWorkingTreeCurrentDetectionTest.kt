// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.util.registry.Registry
import git4idea.GitStandardLocalBranch
import git4idea.GitWorkingTree
import git4idea.actions.ref.GitSingleRefAction
import git4idea.repo.GitRepository
import git4idea.repo.GitWorkingTreeHolderImpl
import git4idea.test.GitSingleRepoTest
import git4idea.test.registerRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class GitWorkingTreeCurrentDetectionTest : GitSingleRepoTest() {

  override fun setUp() {
    super.setUp()
    Registry.get("git.enable.working.trees.feature").setValue(true, testRootDisposable)
  }

  fun `test current branch is not reported as checked out in another worktree`() {
    repo.reloadWorkingTrees()

    assertSameElements(
      repo.workingTreeHolder.getWorkingTrees(),
      listOf(GitWorkingTree(repo.root.path, repo.currentBranch!!.fullName, true, true)),
    )
    assertNull(GitSingleRefAction.getWorkingTreeWithRef(repo.currentBranch!!, repo, skipCurrentWorkingTree = true))
  }

  fun `test branch in a sibling worktree is reported as checked out elsewhere`() {
    val featurePath = testNioRoot.resolve("feature")
    git("worktree add -b feature $featurePath")
    repo.reloadWorkingTrees()

    val trees = repo.workingTreeHolder.getWorkingTrees()
    assertSameElements(
      trees,
      listOf(
        GitWorkingTree(repo.root.path, repo.currentBranch!!.fullName, true, true),
        GitWorkingTree(featurePath.toString(), "refs/heads/feature", false, false),
      ),
    )

    val featureTree = trees.single { !it.isMain }
    assertEquals(featureTree, GitSingleRefAction.getWorkingTreeWithRef(GitStandardLocalBranch("feature"), repo, skipCurrentWorkingTree = true))
    assertNull(GitSingleRefAction.getWorkingTreeWithRef(repo.currentBranch!!, repo, skipCurrentWorkingTree = true))
  }

  fun `test worktree nested inside the main repo directory is detected`() {
    val nestedPath = projectNioRoot.resolve("nested")
    git("worktree add -b nested $nestedPath")
    repo.reloadWorkingTrees()

    val trees = repo.workingTreeHolder.getWorkingTrees()
    assertSameElements(
      trees,
      listOf(
        GitWorkingTree(repo.root.path, repo.currentBranch!!.fullName, true, true),
        GitWorkingTree(nestedPath.toString(), "refs/heads/nested", false, false),
      ),
    )

    val nestedTree = trees.single { !it.isMain }
    assertEquals(nestedTree, GitSingleRefAction.getWorkingTreeWithRef(GitStandardLocalBranch("nested"), repo, skipCurrentWorkingTree = true))
  }

  fun `test from a linked worktree the main branch is reported as checked out elsewhere`() {
    val mainBranch = repo.currentBranch!!
    val featurePath = testNioRoot.resolve("feature")
    git("worktree add -b feature $featurePath")

    val linkedRepo = registerRepo(project, featurePath)
    linkedRepo.reloadWorkingTrees()

    // The linked worktree's own (feature) branch must not be considered busy.
    assertNull(GitSingleRefAction.getWorkingTreeWithRef(GitStandardLocalBranch("feature"), linkedRepo, skipCurrentWorkingTree = true))

    // The main branch is checked out in the (now non-current) main worktree.
    val blocking = GitSingleRefAction.getWorkingTreeWithRef(mainBranch, linkedRepo, skipCurrentWorkingTree = true)
    assertNotNull("Main branch should be reported as checked out in the main worktree", blocking)
    assertTrue("The blocking worktree should be the main one", blocking!!.isMain)
    assertEquals(repo.root.path, blocking.path.path)
  }

  private fun GitRepository.reloadWorkingTrees() {
    runBlocking {
      withContext(Dispatchers.IO) {
        (workingTreeHolder as GitWorkingTreeHolderImpl).updateState()
      }
    }
  }
}
