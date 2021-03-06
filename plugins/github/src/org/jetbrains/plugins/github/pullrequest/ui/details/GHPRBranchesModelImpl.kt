// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.impl.VcsProjectLog
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.GithubGitHelper

internal class GHPRBranchesModelImpl(private val valueModel: SingleValueModel<GHPullRequest>,
                                     detailsDataProvider: GHPRDetailsDataProvider,
                                     override val localRepository: GitRepository,
                                     private val parentDisposable: Disposable) : GHPRBranchesModel {

  private val changeEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

  init {
    registerVCSLogDataPackListener()
  }

  private val dataPackListener = DataPackChangeListener {
    notifyChanged()
    detailsDataProvider.reloadDetails()
  }

  private fun registerVCSLogDataPackListener() {
    val project = localRepository.project
    VcsProjectLog.runWhenLogIsReady(project) {
      val log = VcsProjectLog.getInstance(project)
      val dataManager = log.dataManager ?: return@runWhenLogIsReady
      ApplicationManager.getApplication()
        .invokeLater({
                       with(dataManager) {
                         addDataPackChangeListener(dataPackListener)
                         Disposer.register(parentDisposable, Disposable {
                           removeDataPackChangeListener(dataPackListener)
                         })
                       }
                     }, { Disposer.isDisposed(parentDisposable) })
    }
  }

  @RequiresEdt
  override fun addAndInvokeChangeListener(listener: () -> Unit) =
    SimpleEventListener.addAndInvokeListener(changeEventDispatcher, parentDisposable, listener)

  override val baseBranch: String
    get() = valueModel.value.baseRefName
  override val headBranch: String
    get() {
      with(valueModel.value) {
        if (headRepository == null) return headRefName
        if (headRepository.isFork || baseRefName == headRefName) {
          return headRepository.owner.login + ":" + headRefName
        }
        else {
          return headRefName
        }
      }
    }

  override val prRemote: GitRemote?
    get() = determinePrRemote()

  override val localBranch: String?
    get() = determineLocalBranch()

  private val headRefName: String
    get() = valueModel.value.headRefName

  private fun notifyChanged() {
    changeEventDispatcher.multicaster.eventOccurred()
  }

  private val url: String?
    get() = valueModel.value.headRepository?.url

  private val sshUrl: String?
    get() = valueModel.value.headRepository?.sshUrl

  private val isFork: Boolean
    get() = valueModel.value.headRepository?.isFork ?: false

  private fun determinePrRemote(): GitRemote? = GithubGitHelper.getInstance().findRemote(localRepository, url, sshUrl)

  private fun determineLocalBranch(): String? {
    val prRemote = prRemote ?: return null
    return GithubGitHelper.getInstance().findLocalBranch(localRepository, prRemote, isFork, headRefName)
  }
}
