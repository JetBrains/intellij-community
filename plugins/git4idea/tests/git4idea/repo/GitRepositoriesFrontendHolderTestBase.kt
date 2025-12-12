// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.dvcs.repo.rpcId
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.git.repo.GitRepositoryModel
import git4idea.GitWorkingTree
import git4idea.test.GitSingleRepoTest
import git4idea.test.git
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
abstract class GitRepositoriesFrontendHolderTestBase : GitSingleRepoTest() {
  private val updatesChanel = Channel<GitRepositoriesHolder.UpdateType>(capacity = 1000, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus(GitRepositoriesHolder::class.java.name)

  override fun setUp() {
    super.setUp()

    project.messageBus.connect().subscribe(GitRepositoriesHolder.UPDATES,
                                           GitRepositoriesHolder.UpdatesListener { updateType -> updatesChanel.trySend(updateType) })
  }

  protected fun GitRepositoriesHolder.getTestRepo(): GitRepositoryModel {
    val holderRepo = checkNotNull(get(repo.rpcId()))
    assertEquals(holderRepo.root, repo.root)
    return holderRepo
  }

  protected fun executeAndExpectEvent(
    operation: suspend () -> Unit,
    condition: (currentEvent: GitRepositoriesHolder.UpdateType, previousEvents: List<GitRepositoriesHolder.UpdateType>) -> Boolean,
  ) {
    runBlocking {
      skipEvents()
      operation()
      val collected = mutableListOf<GitRepositoriesHolder.UpdateType>()
      withTimeout(5.seconds) {
        updatesChanel.consumeAsFlow().first {
          LOG.info("Received update: $it")
          collected.add(it)
          condition(it, collected)
        }
      }
    }
  }

  private suspend fun skipEvents() {
    withTimeout(1.seconds) {
      while (!updatesChanel.isEmpty) {
        updatesChanel.tryReceive()
      }
    }
  }

  protected fun doTestWorkingTreeCreation(mainDirectoryRepoPath: Path, vararg initialExpectedWorkingTrees: GitWorkingTree) {
    val holder = GitRepositoriesHolder.getInstance(project)
    runBlocking {
      holder.init()
    }

    assertSameElements(holder.getTestRepo().state.workingTrees, initialExpectedWorkingTrees.toList())

    val branch = "tree"
    val treeRoot = "treeRoot"

    executeAndExpectEvent(
      {
        repo.git("worktree add -B $branch ../$treeRoot")
        val worktreesDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(mainDirectoryRepoPath.resolve(".git/worktrees"))
        refresh(worktreesDir!!)
      },
      { event, _ ->
        return@executeAndExpectEvent event == GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED
      }
    )

    val workingTrees = holder.getTestRepo().state.workingTrees
    val expected = initialExpectedWorkingTrees.toMutableList()
    expected.add(
      GitWorkingTree("${testNioRoot.pathString}/$treeRoot", GitRefUtil.addRefsHeadsPrefixIfNeeded(branch)!!, false, false)
    )

    assertSameElements(workingTrees, expected)
  }
}