// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting.ui

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.auth.ServerAccount
import com.intellij.collaboration.auth.ui.LoadingAccountsDetailsProvider
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.AccountSelectorComponentFactory
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.isDefault
import com.intellij.collaboration.ui.SimpleComboboxWithActionsFactory
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.util.ui.*
import git4idea.remote.hosting.HostedGitRepositoryMapping
import kotlinx.coroutines.CoroutineScope
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.layout.PlatformDefaults
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
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
    }

    val submitButton = JButton(submitActionText).apply {
      isDefault = true
      isOpaque = false

      addActionListener {
        vm.submitSelection()
      }

      bindVisibility(scope, vm.submitAvailableState)
    }

    val credsMissingLabel = JLabel(credsMissingText).apply {
      foreground = NamedColorUtil.getErrorForeground()
      isVisible = false
      bindVisibility(scope, vm.missingCredentialsState)
    }

    val actionsPanel = JPanel(HorizontalLayout(UI.scale(16))).apply {
      isOpaque = false
      add(submitButton)
      loginButtons.forEach {
        add(it)
      }

      putClientProperty(PlatformDefaults.VISUAL_PADDING_PROPERTY, submitButton.insets)
    }

    return JPanel(null).apply {
      isOpaque = false
      border = JBUI.Borders.empty(30, 16)
      layout = MigLayout(LC().fill().gridGap("${UI.scale(10)}px", "${UI.scale(16)}px").insets("0").hideMode(3).noGrid())

      add(repoCombo, CC().growX().push())
      add(accountCombo, CC())

      add(credsMissingLabel, CC().newline())

      add(actionsPanel, CC().newline())
      add(JLabel(CollaborationToolsBundle.message("review.login.note")).apply {
        foreground = UIUtil.getContextHelpForeground()
      }, CC().newline().minWidth("0"))
    }
  }
}

