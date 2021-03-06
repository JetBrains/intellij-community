// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create

import com.intellij.util.EventDispatcher
import git4idea.GitBranch
import git4idea.GitRemoteBranch
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GithubUtil.Delegates.observableField

class GHPRCreateDirectionModelImpl(override val baseRepo: GHGitRepositoryMapping) : GHPRCreateDirectionModel {

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
}