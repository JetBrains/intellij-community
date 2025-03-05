// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.util.EventDispatcher
import git4idea.GitBranch
import git4idea.GitRemoteBranch
import git4idea.ui.branch.MergeDirectionModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping

internal class GHPRCreateMergeDirectionModel(cs: CoroutineScope, private val vm: GHPRCreateViewModel)
  : MergeDirectionModel<GHGitRepositoryMapping> {

  private val changeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  init {
    cs.launchNow {
      withContext(Dispatchers.Main) {
        vm.branches.drop(1).collect {
          changeEventDispatcher.multicaster.eventOccurred()
        }
      }
    }
  }

  private val direction: GHPRCreateViewModel.BranchesState
    get() = vm.branches.value

  override val baseRepo: GHGitRepositoryMapping by direction::baseRepo

  override var baseBranch: GitRemoteBranch?
    get() = direction.baseBranch
    set(value) = vm.setBaseBranch(value)

  override val headRepo: GHGitRepositoryMapping?
    get() = direction.headRepo
  override val headBranch: GitBranch?
    get() = direction.headBranch

  override var headSetByUser: Boolean = false

  override fun setHead(repo: GHGitRepositoryMapping?, branch: GitBranch?) {
    vm.setHead(repo, branch)
  }

  override fun addAndInvokeDirectionChangesListener(listener: () -> Unit) =
    SimpleEventListener.addAndInvokeListener(changeEventDispatcher, listener)

  override fun getKnownRepoMappings(): List<GHGitRepositoryMapping> = vm.repositories.toList()
}