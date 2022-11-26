// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting.ui

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.auth.ServerAccount
import com.intellij.collaboration.auth.ui.LoadingAccountsDetailsProvider
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.AccountSelectorComponentFactory
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.isDefault
import com.intellij.collaboration.ui.ExceptionUtil
import com.intellij.collaboration.ui.SimpleComboboxWithActionsFactory
import com.intellij.collaboration.ui.codereview.BaseHtmlEditorPane
import com.intellij.collaboration.ui.util.bindDisabled
import com.intellij.collaboration.ui.util.bindText
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.ui.AnimatedIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import git4idea.remote.hosting.HostedGitRepositoryMapping
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.layout.PlatformDefaults
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.*

private const val AVATAR_SIZE = 20
private const val AVATAR_SIZE_POPUP = 40

class RepositoryAndAccountSelectorComponentFactory<M : HostedGitRepositoryMapping, A : ServerAccount>(
  private val vm: RepositoryAndAccountSelectorViewModel<M, A>
) {

  fun create(
    scope: CoroutineScope,
    repoNamer: (M) -> @Nls String,
    detailsProvider: LoadingAccountsDetailsProvider<A, *>,
    accountsPopupActionsSupplier: (M) -> List<Action>,
    credsMissingText: @Nls String,
    submitActionText: @Nls String,
    loginButtons: List<JButton>
  ): JComponent {

    val repoCombo = SimpleComboboxWithActionsFactory(vm.repositoriesState, vm.repoSelectionState).create(scope, { mapping ->
      SimpleComboboxWithActionsFactory.Presentation(
        repoNamer(mapping),
        mapping.remote.remote.name
      )
    }).apply {
      putClientProperty(PlatformDefaults.VISUAL_PADDING_PROPERTY, insets)
      bindDisabled(scope, vm.busyState)
    }

    val accountCombo = AccountSelectorComponentFactory(vm.accountsState, vm.accountSelectionState).create(
      scope,
      detailsProvider,
      AVATAR_SIZE,
      AVATAR_SIZE_POPUP,
      CollaborationToolsBundle.message("account.choose.link"),
      vm.repoSelectionState.mapState(scope) { if (it == null) emptyList() else accountsPopupActionsSupplier(it) }
    ).apply {
      putClientProperty(PlatformDefaults.VISUAL_PADDING_PROPERTY, insets)
      bindDisabled(scope, vm.busyState)
    }

    val submitButton = JButton(submitActionText).apply {
      isDefault = true
      isOpaque = false

      addActionListener {
        vm.submitSelection()
      }

      bindVisibility(scope, vm.submitAvailableState)
      bindDisabled(scope, vm.busyState)
    }

    val errorPanel = JPanel(BorderLayout()).apply {
      val iconPanel = JPanel(BorderLayout()).apply {
        val iconLabel = JLabel(AllIcons.Ide.FatalError)
        border = JBUI.Borders.emptyRight(iconLabel.iconTextGap)
        add(iconLabel, BorderLayout.NORTH)
      }

      val errorTextPane = BaseHtmlEditorPane().apply {
        bindText(scope, vm.errorState.map {
          when (it) {
            is RepositoryAndAccountSelectorViewModel.Error.MissingCredentials -> credsMissingText
            is RepositoryAndAccountSelectorViewModel.Error.SubmissionError -> {
              HtmlBuilder()
                .append(CollaborationToolsBundle.message("review.list.connection.failed", it.repo.repository.toString(), it.account))
                .br()
                .append(ExceptionUtil.getPresentableMessage(it.exception))
                .toString()
            }
            null -> ""
          }
        })
      }
      add(iconPanel, BorderLayout.WEST)
      add(errorTextPane, BorderLayout.CENTER)

      bindVisibility(scope, vm.errorState.map { it != null })
    }
    val busyLabel = JLabel(AnimatedIcon.Default()).apply {
      bindVisibility(scope, vm.busyState)
    }


    val actionsPanel = JPanel(HorizontalLayout(UI.scale(16))).apply {
      isOpaque = false
      add(submitButton)
      loginButtons.forEach {
        add(it)
      }
      add(busyLabel)

      putClientProperty(PlatformDefaults.VISUAL_PADDING_PROPERTY, submitButton.insets)
    }

    return JPanel(null).apply {
      isOpaque = false
      border = JBUI.Borders.empty(30, 16)
      layout = MigLayout(LC().fill().gridGap("${UI.scale(10)}px", "${UI.scale(16)}px").insets("0").hideMode(3).noGrid())

      add(repoCombo, CC().growX().push())
      add(accountCombo, CC())

      add(actionsPanel, CC().newline())
      add(errorPanel, CC().newline())
      add(JLabel(CollaborationToolsBundle.message("review.login.note")).apply {
        foreground = UIUtil.getContextHelpForeground()
      }, CC().newline().minWidth("0"))
    }
  }
}

