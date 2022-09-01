// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import git4idea.remote.hosting.knownRepositories
import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.util.EventDispatcher
import git4idea.GitBranch
import git4idea.GitRemoteBranch
import git4idea.ui.branch.MergeDirectionModel
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.observableField

class GHPRMergeDirectionModelImpl(override val baseRepo: GHGitRepositoryMapping,
                                  private val repositoriesManager: GHHostedRepositoriesManager) : MergeDirectionModel<GHGitRepositoryMapping> {

  private val changeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override var baseBranch: GitRemoteBranch? by observableField(null, changeEventDispatcher)
  override var headRepo: GHGitRepositoryMapping? = null
    private set
  override var headBranch: GitBranch? = null
    private set
  override var headSetByUser: Boolean = false

  override fun setHead(repo: GHGitRepositoryMapping?, branch: GitBranch?) {
    headRepo = repo
    headBranch = branch
    changeEventDispatcher.multicaster.eventOccurred()
  }

  override fun addAndInvokeDirectionChangesListener(listener: () -> Unit) =
    SimpleEventListener.addAndInvokeListener(changeEventDispatcher, listener)

  override fun getKnownRepoMappings(): List<GHGitRepositoryMapping> = repositoriesManager.knownRepositories.toList()
}