// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.authentication.accounts.AccountTokenChangedListener
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
import org.jetbrains.plugins.github.ui.util.DisposingWrapper
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import org.jetbrains.plugins.github.util.GithubUIUtil

class GHPRRequestExecutorComponent(private val requestExecutorManager: GithubApiRequestExecutorManager,
                                   private val project: Project,
                                   private val remoteUrl: GitRemoteUrlCoordinates,
                                   val account: GithubAccount,
                                   parentDisposable: Disposable)
  : DisposingWrapper(parentDisposable) {

  private val componentFactory by lazy(LazyThreadSafetyMode.NONE) { project.service<GHPRComponentFactory>() }

  private var requestExecutor: GithubApiRequestExecutor? = null

  init {
    background = UIUtil.getListBackground()

    ApplicationManager.getApplication().messageBus.connect(parentDisposable)
      .subscribe(GithubAccountManager.ACCOUNT_TOKEN_CHANGED_TOPIC,
                 object : AccountTokenChangedListener {
                   override fun tokenChanged(account: GithubAccount) {
                     update()
                   }
                 })
    update()
  }

  private fun update() {
    if (requestExecutor != null) return

    try {
      val executor = requestExecutorManager.getExecutor(account)
      setActualContent(executor)
    }
    catch (e: Exception) {
      setCenteredContent(GithubUIUtil.createNoteWithAction(::createRequestExecutorWithUserInput).apply {
        append("Log in", SimpleTextAttributes.LINK_ATTRIBUTES, Runnable { createRequestExecutorWithUserInput() })
        append(" to GitHub to view pull requests", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      })
    }
  }

  private fun createRequestExecutorWithUserInput() {
    requestExecutorManager.getExecutor(account, project)
    IdeFocusManager.getInstance(project).requestFocusInProject(this@GHPRRequestExecutorComponent, project)
  }

  private fun setActualContent(executor: GithubApiRequestExecutor.WithTokenAuth) {
    requestExecutor = executor
    val disposable = Disposer.newDisposable()
    setContent(componentFactory.createComponent(remoteUrl, account, executor, disposable), disposable)
  }
}