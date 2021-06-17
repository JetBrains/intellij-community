// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.openapi.util.NlsSafe
import com.intellij.util.EventDispatcher
import git4idea.GitBranch
import git4idea.GitRemoteBranch
import git4idea.ui.branch.CreateMergeDirectionModel
import com.intellij.collaboration.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.observableField

class GHPRCreateMergeDirectionModelImpl(override val baseRepo: GHGitRepositoryMapping) : CreateMergeDirectionModel<GHGitRepositoryMapping> {

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

  @NlsSafe
  override fun getBaseRepoText(): String? {
    val branch = baseBranch ?: return null
    val headRepoPath = headRepo?.repository?.repositoryPath
    val baseRepoPath = baseRepo.repository.repositoryPath
    val showOwner = headRepoPath != null && baseRepoPath != headRepoPath
    return baseRepo.repository.repositoryPath.toString(showOwner) + ":" + branch.name
  }

  @NlsSafe
  override fun getHeadRepoText(): String? {
    val branch = headBranch ?: return null
    val headRepoPath = headRepo?.repository?.repositoryPath ?: return null
    val baseRepoPath = baseRepo.repository.repositoryPath
    val showOwner = baseRepoPath != headRepoPath
    return headRepoPath.toString(showOwner) + ":" + branch.name
  }
}