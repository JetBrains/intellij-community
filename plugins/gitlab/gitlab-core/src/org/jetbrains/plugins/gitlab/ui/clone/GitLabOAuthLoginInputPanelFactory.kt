// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.asObservableIn
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.util.bindComboBoxTextIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindValidationOnApplyIn
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginErrorStatusPresenter
import org.jetbrains.plugins.gitlab.authentication.ui.GitLabOAuthLoginViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle

internal object GitLabOAuthLoginInputPanelFactory {
  fun createIn(
    cs: CoroutineScope,
    vm: GitLabOAuthLoginViewModel,
    serverFieldDisabled: Boolean = false,
    canLogInWithGit: Boolean = false,
    footer: Panel.() -> Unit = { },
  ): DialogPanel {
    val editable = vm.isLoggingIn.mapState { !it }
    val errorPresenter = GitLabLoginErrorStatusPresenter(vm, canLogInWithGit)

    return panel {
      row(CollaborationToolsBundle.message("login.field.server")) {
        comboBox(vm.servers)
          .applyToComponent {
            isEditable = true
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
          .comment(GitLabBundle.message("account.oauth.client.id.empty"), maxLineLength = MAX_LINE_LENGTH_WORD_WRAP)
          .align(AlignX.FILL)
          .resizableColumn().enabledIf(editable.asObservableIn(cs))
          .focused()
      }
      row {
        cell(ErrorStatusPanelFactory.create(cs, vm.errorFlow, errorPresenter, ErrorStatusPanelFactory.Alignment.LEFT))
      }
      footer()
    }
  }
}
