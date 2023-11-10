// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.create.model

import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.util.EventDispatcher
import git4idea.GitBranch
import git4idea.GitRemoteBranch
import git4idea.remote.hosting.knownRepositories
import git4idea.ui.branch.MergeDirectionModel
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.util.GitLabProjectMapping

internal class GitLabMergeRequestCreateDirectionModel(
  private val repositoriesManager: GitLabProjectsManager,
  override val baseRepo: GitLabProjectMapping
) : MergeDirectionModel<GitLabProjectMapping> {
  private val changeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  override var baseBranch: GitRemoteBranch? = null
    set(value) {
      field = value
      changeEventDispatcher.multicaster.eventOccurred()
    }

  override var headRepo: GitLabProjectMapping? = null
    private set
  override var headBranch: GitBranch? = null
    private set
  override var headSetByUser: Boolean = false

  fun addDirectionChangesListener(listener: () -> Unit) {
    return SimpleEventListener.addListener(changeEventDispatcher, listener)
  }

  override fun addAndInvokeDirectionChangesListener(listener: () -> Unit) {
    return SimpleEventListener.addAndInvokeListener(changeEventDispatcher, listener)
  }

  override fun getKnownRepoMappings(): List<GitLabProjectMapping> {
    return repositoriesManager.knownRepositories.toList()
  }

  override fun setHead(repo: GitLabProjectMapping?, branch: GitBranch?) {
    headRepo = repo
    headBranch = branch
    changeEventDispatcher.multicaster.eventOccurred()
  }
}