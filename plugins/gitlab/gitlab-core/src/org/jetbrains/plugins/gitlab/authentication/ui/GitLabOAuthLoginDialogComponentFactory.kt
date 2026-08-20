// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.collaboration.auth.ui.login.LoginModel.LoginState
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.AnimatedIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.GitLabCredentials
import org.jetbrains.plugins.gitlab.ui.clone.GitLabOAuthLoginInputPanelFactory
import org.jetbrains.plugins.gitlab.ui.util.GitLabPluginProjectScopeProvider
import javax.swing.JComponent

private const val SELF_MANAGED_SERVER_OAUTH_CONFIGURATION_HELP_ID = "self-managed-server-oauth-configuration"
private const val SELF_MANAGED_SERVER_OAUTH_CONFIGURATION_DOCS_LINK =
  "https://www.jetbrains.com/help/idea/set-up-a-gitlab-account.html#self-managed-server-oauth-configuration"

internal object GitLabOAuthLoginDialogComponentFactory {
  fun showIn(
    project: Project,
    parentComponent: JComponent?,
    title: @NlsContexts.DialogTitle String,
    requiredServerPath: GitLabServerPath?,
    serverFieldDisabled: Boolean,
    canLogInWithGit: Boolean,
    requiredUsername: String?,
    uniqueAccountPredicate: (GitLabServerPath, String) -> Boolean,
  ): GitLabOAuthLoginOutcome {
    val scopeProvider = project.service<GitLabPluginProjectScopeProvider>()
    val dialog = scopeProvider.constructDialog("GitLab OAuth login dialog") {
      val cs = this
      val vm = GitLabOAuthLoginViewModel(project, cs, requiredServerPath, requiredUsername, uniqueAccountPredicate)
      GitLabOAuthLoginDialog(project, cs, parentComponent, title, serverFieldDisabled, canLogInWithGit, vm)
    }
    dialog.showAndGet()
    return dialog.loginOutcome ?: GitLabOAuthLoginOutcome.Cancelled
  }
}

internal sealed interface GitLabOAuthLoginOutcome {
  data class Success(
    val serverPath: GitLabServerPath,
    val credentials: GitLabCredentials.OAuth,
    val username: String,
  ) : GitLabOAuthLoginOutcome

  data object Cancelled : GitLabOAuthLoginOutcome
  data object OtherMethod : GitLabOAuthLoginOutcome
}

@Suppress("SplitModeApiUsage")
private class GitLabOAuthLoginDialog(
  project: Project,
  parentCs: CoroutineScope,
  parentComponent: JComponent?,
  title: @NlsContexts.DialogTitle String,
  private val serverFieldDisabled: Boolean,
  private val canLogInWithGit: Boolean,
  private val vm: GitLabOAuthLoginViewModel,
) : DialogWrapper(project, parentComponent, false, IdeModalityType.IDE) {

  private val cs = parentCs.childScope(javaClass.name, Dispatchers.EDT + ModalityState.stateForComponent(rootPane).asContextElement())

  var loginOutcome: GitLabOAuthLoginOutcome? = null
    private set

  init {
    setTitle(title)
    setOKButtonText(CollaborationToolsBundle.message("login.button"))
    init()

    cs.launch {
      vm.isLoggingIn.collect {
        if (it) {
          isOKActionEnabled = false
          setOKButtonText(CollaborationToolsBundle.message("login.progress"))
          setOKButtonIcon(AnimatedIcon.Default())
        }
        else {
          isOKActionEnabled = true
          setOKButtonText(CollaborationToolsBundle.message("login.button"))
          setOKButtonIcon(null)
        }
      }
    }
    cs.launch { vm.loginState.collect { if (it is LoginState.Failed) startTrackingValidation() } }
    cs.launch {
      vm.outcome.collect { outcome ->
        loginOutcome = outcome ?: return@collect
        val exitCode = when (outcome) {
          is GitLabOAuthLoginOutcome.Success -> OK_EXIT_CODE
          GitLabOAuthLoginOutcome.Cancelled -> CANCEL_EXIT_CODE
          GitLabOAuthLoginOutcome.OtherMethod -> NEXT_USER_EXIT_CODE
        }
        close(exitCode)
      }
    }
  }

  override fun doOKAction() = vm.requestLogin()

  override fun getHelpId(): String = SELF_MANAGED_SERVER_OAUTH_CONFIGURATION_HELP_ID

  override fun doHelpAction() = BrowserUtil.browse(SELF_MANAGED_SERVER_OAUTH_CONFIGURATION_DOCS_LINK)

  override fun createCenterPanel(): DialogPanel =
    GitLabOAuthLoginInputPanelFactory.createIn(cs, vm, serverFieldDisabled, canLogInWithGit).withPreferredWidth(350)
}
