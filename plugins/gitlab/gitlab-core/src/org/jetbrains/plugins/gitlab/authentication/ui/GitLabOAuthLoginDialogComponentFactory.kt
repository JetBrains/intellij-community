// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.ui

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.auth.ui.login.LoginModel.LoginState
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.asObservableIn
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.util.bindComboBoxTextIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindValidationOnApplyIn
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
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.authentication.GitLabCredentials
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginErrorStatusPresenter
import org.jetbrains.plugins.gitlab.ui.util.GitLabPluginProjectScopeProvider
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent

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

    cs.launch { vm.isLoggingIn.collect { isOKActionEnabled = !it } }
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

  override fun createCenterPanel(): DialogPanel {
    val progressExtension = ExtendableTextComponent.Extension
      .create(AnimatedIcon.Default(), CollaborationToolsBundle.message("login.progress"), null)
    val editable = vm.isLoggingIn.mapState { !it }
    val errorPresenter = GitLabLoginErrorStatusPresenter(vm, canLogInWithGit)

    return panel {
      row(CollaborationToolsBundle.message("login.field.server")) {
        comboBox(vm.servers)
          .applyToComponent {
            isEditable = true
            val editorField = editor.editorComponent as? ExtendableTextComponent
            if (editorField != null) {
              cs.launch {
                vm.isLoggingIn.collect { inProgress ->
                  if (inProgress) editorField.addExtension(progressExtension)
                  else editorField.removeExtension(progressExtension)
                }
              }
            }
          }
          .bindComboBoxTextIn(cs, vm.serverUri.valueFlow)
          .bindValidationOnApplyIn(cs, vm.serverUri)
          .align(AlignX.FILL)
          .resizableColumn().enabledIf(editable.mapState { it && !serverFieldDisabled }.asObservableIn(cs))
      }
      row(GitLabBundle.message("account.oauth.client.id.label")) {
        textField()
          .bindTextIn(cs, vm.clientId.valueFlow)
          .bindValidationOnApplyIn(cs, vm.clientId)
          .align(AlignX.FILL)
          .resizableColumn().enabledIf(editable.asObservableIn(cs))
          .focused()
      }
      row {
        cell(ErrorStatusPanelFactory.create(cs, vm.errorFlow, errorPresenter, ErrorStatusPanelFactory.Alignment.LEFT))
      }
    }.withPreferredWidth(350)
  }
}
