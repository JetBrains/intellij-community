// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.ui

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.asObservableIn
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindValidationOnApplyIn
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil.browse
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil.getRegularPanelInsets
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil.buildNewTokenUrl
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException
import org.jetbrains.plugins.github.exceptions.GithubParseException
import org.jetbrains.plugins.github.i18n.GithubBundle.message
import java.net.UnknownHostException
import javax.swing.Action
import javax.swing.JLabel

internal object GHTokenLoginPanelFactory {
  fun createIn(
    cs: CoroutineScope, vm: GHTokenLoginViewModel,
    isServerEditable: Boolean = true,
    footer: Panel.() -> Unit = {},
  ): DialogPanel {
    val isInputEnabled = vm.isLoggingIn.mapState { !it }.asObservableIn(cs)
    val focusServer = isServerEditable && vm.server.valueFlow.value.isBlank()
    val hasError = vm.errorFlow.mapState { it != null }
    val errorPresenter = object : ErrorStatusPresenter.Text<Throwable> {
      override fun getErrorTitle(error: Throwable): String = getLoginErrorMessage(error)
      override fun getErrorDescription(error: Throwable): String? = null
      override fun getErrorAction(error: Throwable): Action? = null
    }

    val serverField = ExtendableTextField(0).apply {
      isEditable = isServerEditable
      bindTextIn(cs, vm.server.valueFlow) { vm.server.valueFlow.value = it }
      bindProgressIn(cs, vm)
    }
    val tokenField = JBPasswordField().apply {
      bindTextIn(cs, vm.token.valueFlow) { vm.token.valueFlow.value = it }
    }

    return panel {
      row(message("credentials.server.field")) {
        cell(serverField)
          .bindValidationOnApplyIn(cs, vm.server)
          .enabledIf(isInputEnabled)
          .align(AlignX.FILL)
          .apply { if (focusServer) focused() }
      }
      row(message("credentials.token.field")) {
        cell(tokenField)
          .bindValidationOnApplyIn(cs, vm.token)
          .enabledIf(isInputEnabled)
          .comment(CollaborationToolsBundle.message("clone.dialog.insufficient.scopes", GHSecurityUtil.MASTER_SCOPES),
                   maxLineLength = MAX_LINE_LENGTH_WORD_WRAP)
          .align(AlignX.FILL)
          .resizableColumn()
          .apply { if (!focusServer) focused() }
        button(message("credentials.button.generate")) { vm.serverPath.value?.let { browse(buildNewTokenUrl(it)) } }
          .enabledIf(vm.serverPath.mapState { it != null }.asObservableIn(cs))
      }
      row("") {
        val iconLabel = JLabel(AllIcons.Ide.FatalError).apply {
          border = JBUI.Borders.emptyRight(iconTextGap)
        }
        val errorComponent = ErrorStatusPanelFactory.create(cs, vm.errorFlow, errorPresenter, ErrorStatusPanelFactory.Alignment.LEFT)

        val errorWithIcon = BorderLayoutPanel().apply {
          isOpaque = false
          addToLeft(iconLabel)
          addToCenter(errorComponent)
        }

        cell(errorWithIcon)
          .align(AlignX.FILL)
          .resizableColumn()
      }.visibleIf(hasError.asObservableIn(cs))

      footer()
    }.apply {
      // Border is required to have more space - otherwise there could be issues with focus ring.
      // `getRegularPanelInsets()` is used to simplify border calculation for dialogs where this panel is used.
      border = JBUI.Borders.empty(getRegularPanelInsets())
    }
  }

  private fun ExtendableTextField.bindProgressIn(cs: CoroutineScope, vm: GHTokenLoginViewModel) {
    val progressIcon = AnimatedIcon.Default()
    val progressExtension = ExtendableTextComponent.Extension { progressIcon }
    cs.launch {
      vm.isLoggingIn.collect { loggingIn ->
        if (loggingIn) addExtension(progressExtension) else removeExtension(progressExtension)
      }
    }
  }
}

internal fun getLoginErrorMessage(error: Throwable): @NlsContexts.DialogMessage String =
  when (error) {
    is GithubParseException -> error.message ?: message("credentials.invalid.server.path")
    is LoginNotUniqueException -> message("login.account.already.added", error.login)
    is UnknownHostException -> message("server.unreachable")
    is GithubAuthenticationException -> message("credentials.incorrect", error.message.orEmpty())
    else -> message("credentials.invalid.auth.data", error.message.orEmpty())
  }
