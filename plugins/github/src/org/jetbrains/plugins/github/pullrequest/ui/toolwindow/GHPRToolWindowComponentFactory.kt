// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.SingleComponentCenteringLayout
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.AccountTokenChangedListener
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder
import org.jetbrains.plugins.github.authentication.ui.GithubChooseAccountDialog
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingErrorHandlerImpl
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingPanelFactory
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates
import org.jetbrains.plugins.github.util.GithubUIUtil
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.properties.Delegates

internal class GHPRToolWindowComponentFactory(private val project: Project,
                                              private val remoteUrl: GitRemoteUrlCoordinates,
                                              private val parentDisposable: Disposable) {
  private val dataContextRepository = GHPRDataContextRepository.getInstance(project)

  @CalledInAwt
  fun createComponent(): JComponent {
    val panel = JPanel().apply {
      background = UIUtil.getListBackground()
    }
    Controller(panel)
    return panel
  }

  private inner class Controller(private val panel: JPanel) {
    private var selectedAccount: GithubAccount? = null
    private var requestExecutor: GithubApiRequestExecutor? = null

    private var contentDisposable by Delegates.observable<Disposable?>(null) { _, oldValue, newValue ->
      if (oldValue != null) Disposer.dispose(oldValue)
      if (newValue != null) Disposer.register(parentDisposable, newValue)
    }

    init {
      ApplicationManager.getApplication().messageBus.connect(parentDisposable)
        .subscribe(GithubAccountManager.ACCOUNT_TOKEN_CHANGED_TOPIC, object : AccountTokenChangedListener {
          override fun tokenChanged(account: GithubAccount) {
            ApplicationManager.getApplication().invokeLater(Runnable { update() }, project.disposed)
          }
        })
      update()
    }

    private fun update() {
      val authManager = GithubAuthenticationManager.getInstance()
      if (selectedAccount == null) {
        val accounts = authManager.getAccounts().filter { it.server.matches(remoteUrl.url) }

        if (accounts.size == 1) {
          selectedAccount = accounts.single()
        }

        val defaultAccount = accounts.find { it == authManager.getDefaultAccount(project) }
        if (defaultAccount != null) {
          selectedAccount = defaultAccount
        }

        if (accounts.isNotEmpty()) {
          showChooseAccountPanel(accounts)
        }
        else {
          showLoginPanel()
        }
      }
      val account = selectedAccount ?: return

      if (requestExecutor == null) {
        try {
          requestExecutor = GithubApiRequestExecutorManager.getInstance().getExecutor(account)
        }
        catch (e: Exception) {
          showCreateRequestExecutorPanel(account)
        }
      }
      val executor = requestExecutor ?: return

      if (contentDisposable == null) {
        showPullRequestsComponent(account, executor)
      }
      else {
        if (!authManager.getAccounts().contains(account)) {
          selectedAccount = null
          requestExecutor = null
          update()
        }
      }
    }

    private fun showLoginPanel() {
      setCenteredContent(GithubUIUtil.createNoteWithAction(::requestNewAccount).apply {
        append(GithubBundle.message("login.link"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, Runnable { requestNewAccount() })
        append(" ")
        append(GithubBundle.message("pull.request.account.to.view.prs.suffix"), SimpleTextAttributes.GRAYED_ATTRIBUTES)
      })
    }

    private fun requestNewAccount() {
      GithubAuthenticationManager.getInstance().requestNewAccount(project)
      update()
      GithubUIUtil.focusPanel(panel)
    }

    private fun showChooseAccountPanel(accounts: List<GithubAccount>) {
      setCenteredContent(GithubUIUtil.createNoteWithAction { chooseAccount(accounts) }.apply {
        append(GithubBundle.message("account.choose.link"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
               Runnable { chooseAccount(accounts) })
        append(" ")
        append(GithubBundle.message("pull.request.account.to.view.prs.suffix"), SimpleTextAttributes.GRAYED_ATTRIBUTES)
      })
    }

    private fun chooseAccount(accounts: List<GithubAccount>) {
      val dialog = GithubChooseAccountDialog(project, null, accounts, null, true, true)
      if (dialog.showAndGet()) {
        selectedAccount = dialog.account
        if (dialog.setDefault) project.service<GithubProjectDefaultAccountHolder>().account = dialog.account
        update()
        GithubUIUtil.focusPanel(panel)
      }
    }

    private fun showCreateRequestExecutorPanel(account: GithubAccount) {
      setCenteredContent(GithubUIUtil.createNoteWithAction { createRequestExecutorWithUserInput(account) }.apply {
        append(GithubBundle.message("login.link"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
               Runnable { createRequestExecutorWithUserInput(account) })
        append(" ")
        append(GithubBundle.message("pull.request.account.to.view.prs.suffix"), SimpleTextAttributes.GRAYED_ATTRIBUTES)
      })
    }

    private fun createRequestExecutorWithUserInput(account: GithubAccount) {
      requestExecutor = GithubApiRequestExecutorManager.getInstance().getExecutor(account, project)
      update()
      GithubUIUtil.focusPanel(panel)
    }

    private fun setCenteredContent(component: JComponent) {
      contentDisposable = null

      with(panel) {
        removeAll()
        layout = SingleComponentCenteringLayout()
        add(component)
        validate()
        repaint()
      }
    }

    private fun showPullRequestsComponent(account: GithubAccount, requestExecutor: GithubApiRequestExecutor) {
      val newDisposable = Disposer.newDisposable()
      contentDisposable = newDisposable
      val component = createDataContextLoadingPanel(account, requestExecutor, newDisposable)

      with(panel) {
        removeAll()
        layout = BorderLayout()
        add(component, BorderLayout.CENTER)
        validate()
        repaint()
      }
    }

    private fun createDataContextLoadingPanel(account: GithubAccount,
                                              requestExecutor: GithubApiRequestExecutor,
                                              disposable: Disposable): JComponent {

      val uiDisposable = Disposer.newDisposable()
      Disposer.register(disposable, Disposable {
        Disposer.dispose(uiDisposable)
        dataContextRepository.clearContext(remoteUrl)
      })

      val loadingModel = GHCompletableFutureLoadingModel<GHPRDataContext>(uiDisposable).apply {
        future = dataContextRepository.acquireContext(remoteUrl, account, requestExecutor)
      }

      return GHLoadingPanelFactory(loadingModel, null, GithubBundle.message("cannot.load.data.from.github"),
                                   GHLoadingErrorHandlerImpl(project, account) {
                                     val contextRepository = dataContextRepository
                                     contextRepository.clearContext(remoteUrl)
                                     loadingModel.future = contextRepository.acquireContext(remoteUrl, account, requestExecutor)
                                   }).create { _, result ->
        GHPRComponentFactory(project).createComponent(result, uiDisposable)
      }
    }
  }
}
