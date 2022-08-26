// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.disposingMainScope
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.AccountSelectorComponentFactory
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.isDefault
import com.intellij.collaboration.ui.SimpleComboboxWithActionsFactory
import com.intellij.collaboration.ui.codereview.avatar.CachingCircleImageIconsProvider
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.cloneDialog.VcsCloneDialogUiSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.layout.PlatformDefaults
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.GithubIcons
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountInformationProvider
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.util.CachingGHUserAvatarLoader
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import java.awt.Image
import java.awt.event.ActionEvent
import java.util.concurrent.CompletableFuture
import javax.swing.*

//TODO: pass login actions from outside
class GHPRRepositorySelectorComponentFactory internal constructor(private val project: Project,
                                                                  private val vm: GHPRRepositorySelectorViewModel,
                                                                  private val authManager: GithubAuthenticationManager) {

  fun create(scope: CoroutineScope): JComponent {
    val repoCombo = SimpleComboboxWithActionsFactory(vm.repositoriesState, vm.repoSelectionState).create(scope, { mapping ->
      val allRepositories = vm.repositoriesState.value.map { it.repository }
      SimpleComboboxWithActionsFactory.Presentation(
        GHUIUtil.getRepositoryDisplayName(allRepositories, mapping.repository, true),
        mapping.remote.remote.name
      )
    }).apply {
      putClientProperty(PlatformDefaults.VISUAL_PADDING_PROPERTY, insets)
    }

    val accountCombo = AccountSelectorComponentFactory(vm.accountsState, vm.accountSelectionState).create(
      scope,
      AccountAvatarIconsProvider(),
      GHUIUtil.AVATAR_SIZE,
      VcsCloneDialogUiSpec.Components.popupMenuAvatarSize,
      GithubBundle.message("account.choose.link"),
      vm.repoSelectionState.mapState(scope, ::createPopupLoginActions)
    ).apply {
      putClientProperty(PlatformDefaults.VISUAL_PADDING_PROPERTY, insets)
    }


    val submitButton = JButton(GithubBundle.message("pull.request.view.list")).apply {
      isDefault = true
      isOpaque = false

      addActionListener {
        vm.submitSelection()
      }

      bindVisibility(scope, vm.submitAvailableState)
    }

    val githubLoginButton = JButton(GithubBundle.message("action.Github.Accounts.AddGHAccount.text")).apply {
      isDefault = true
      isOpaque = false

      addActionListener {
        if (loginToGithub(true)) {
          vm.submitSelection()
        }
      }

      bindVisibility(scope, vm.githubLoginAvailableState)
    }

    val tokenLoginLink = ActionLink(GithubBundle.message("action.Github.Accounts.AddGHAccountWithToken.text")) {
      if (loginToGithub(false)) {
        vm.submitSelection()
      }
    }.apply {
      bindVisibility(scope, vm.githubLoginAvailableState)
    }

    val gheLoginButton = JButton(GithubBundle.message("action.Github.Accounts.AddGHEAccount.text")).apply {
      isDefault = true
      isOpaque = false

      addActionListener {
        val repo = vm.repoSelectionState.value ?: return@addActionListener
        if (loginToGhe(false, repo)) {
          vm.submitSelection()
        }
      }

      bindVisibility(scope, vm.gheLoginAvailableState)
    }

    val tokenMissingLabel = JLabel(GithubBundle.message("account.token.missing")).apply {
      foreground = UIUtil.getErrorForeground()
      isVisible = false
      bindVisibility(scope, vm.missingCredentialsState)
    }

    val actionsPanel = JPanel(HorizontalLayout(UI.scale(16))).apply {
      isOpaque = false
      add(submitButton)
      add(githubLoginButton)
      add(tokenLoginLink)
      add(gheLoginButton)

      putClientProperty(PlatformDefaults.VISUAL_PADDING_PROPERTY, submitButton.insets)
    }

    return JPanel(null).apply {
      isOpaque = false
      border = JBUI.Borders.empty(30, 16)
      layout = MigLayout(LC().fill().gridGap("${UI.scale(10)}px", "${UI.scale(16)}px").insets("0").hideMode(3).noGrid())

      add(repoCombo, CC().growX().push())
      add(accountCombo, CC())

      add(tokenMissingLabel, CC().newline())

      add(actionsPanel, CC().newline())
      add(JLabel(GithubBundle.message("pull.request.login.note")).apply {
        foreground = UIUtil.getContextHelpForeground()
      }, CC().newline().minWidth("0"))
    }
  }

  private fun createPopupLoginActions(repo: GHGitRepositoryMapping?): List<AbstractAction> {
    val isDotComServer = repo?.repository?.serverPath?.isGithubDotCom ?: false
    return if (isDotComServer)
      listOf(object : AbstractAction(GithubBundle.message("action.Github.Accounts.AddGHAccount.text")) {
        override fun actionPerformed(e: ActionEvent?) {
          loginToGithub(true)
        }
      }, object : AbstractAction(GithubBundle.message("action.Github.Accounts.AddGHAccountWithToken.text")) {
        override fun actionPerformed(e: ActionEvent?) {
          loginToGithub(true, false)
        }
      })
    else listOf(
      object : AbstractAction(GithubBundle.message("action.Github.Accounts.AddGHEAccount.text")) {
        override fun actionPerformed(e: ActionEvent?) {
          loginToGhe(true, repo!!)
        }
      })
  }

  private fun loginToGithub(forceNew: Boolean, withOAuth: Boolean = true): Boolean {
    val account = vm.accountSelectionState.value
    if (account == null || forceNew) {
      return authManager.requestNewAccountForDefaultServer(project, !withOAuth)?.also {
        vm.accountSelectionState.value = it
      } != null
    }
    else if (vm.missingCredentialsState.value) {
      return authManager.requestReLogin(account, project)
    }
    return false
  }

  private fun loginToGhe(forceNew: Boolean, repo: GHGitRepositoryMapping): Boolean {
    val server = repo.repository.serverPath
    val account = vm.accountSelectionState.value
    if (account == null || forceNew) {
      return authManager.requestNewAccountForServer(server, project)?.also {
        vm.accountSelectionState.value = it
      } != null
    }
    else if (vm.missingCredentialsState.value) {
      return authManager.requestReLogin(account, project)
    }
    return false
  }

  private class AccountAvatarIconsProvider : CachingCircleImageIconsProvider<GithubAccount>(GithubIcons.DefaultAvatar) {

    private val requestExecutorManager = GithubApiRequestExecutorManager.getInstance()
    private val accountInformationProvider = GithubAccountInformationProvider.getInstance()
    private val avatarLoader = CachingGHUserAvatarLoader.getInstance()

    override fun loadImageAsync(key: GithubAccount): CompletableFuture<Image?> {
      val executor = requestExecutorManager.getExecutor(key)
      return ProgressManager.getInstance().submitIOTask(EmptyProgressIndicator()) {
        accountInformationProvider.getInformation(executor, it, key)
      }.thenCompose {
        val url = it.avatarUrl ?: return@thenCompose CompletableFuture.completedFuture(null)
        avatarLoader.requestAvatar(executor, url)
      }
    }
  }
}

internal class GHPRRepositorySelectorViewModel(
  repositoriesManager: GHHostedRepositoriesManager,
  accountManager: GHAccountManager,
  private val onSelected : (GHGitRepositoryMapping, GithubAccount) -> Unit
) : Disposable {
  private val scope = disposingMainScope()

  val repositoriesState = repositoriesManager.knownRepositoriesState

  val repoSelectionState = MutableStateFlow<GHGitRepositoryMapping?>(null)

  val accountsState = combineState(scope, accountManager.accountsState, repoSelectionState) { accountsMap, repo ->
    if (repo == null) {
      emptyList()
    }
    else {
      val server = repo.repository.serverPath
      accountsMap.keys.filter { it.server.equals(server, true) }
    }
  }

  val accountSelectionState = MutableStateFlow<GithubAccount?>(null)

  val missingCredentialsState: StateFlow<Boolean> =
    combineState(scope, accountManager.accountsState, accountSelectionState) { accountsMap, account ->
      account?.let { accountsMap[it] } == null
    }

  val submitAvailableState: StateFlow<Boolean> =
    combineState(scope, repoSelectionState, accountSelectionState, missingCredentialsState) { repo, acc, credsMissing ->
      repo != null && acc != null && !credsMissing
    }

  val githubLoginAvailableState: StateFlow<Boolean> =
    combineState(scope, repoSelectionState, accountSelectionState, missingCredentialsState, ::isGithubLoginAvailable)

  private fun isGithubLoginAvailable(repo: GHGitRepositoryMapping?, account: GithubAccount?, credsMissing: Boolean): Boolean {
    if (repo == null) return false
    return repo.repository.serverPath.isGithubDotCom && (account == null || credsMissing)
  }

  val gheLoginAvailableState: StateFlow<Boolean> =
    combineState(scope, repoSelectionState, accountSelectionState, missingCredentialsState, ::isGheLoginVisible)


  private fun isGheLoginVisible(repo: GHGitRepositoryMapping?, account: GithubAccount?, credsMissing: Boolean): Boolean {
    if (repo == null) return false
    return !repo.repository.serverPath.isGithubDotCom && (account == null || credsMissing)
  }

  fun submitSelection() {
    val repo = repoSelectionState.value ?: return
    val accounts = accountSelectionState.value ?: return
    onSelected(repo, accounts)
  }

  override fun dispose() = Unit
}
