// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting.ui

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.auth.DefaultAccountHolder
import com.intellij.collaboration.auth.ServerAccount
import com.intellij.collaboration.auth.ui.AccountsPanelFactory
import com.intellij.collaboration.auth.ui.LoadingAccountsDetailsProvider
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.AccountSelectorComponentFactory
import com.intellij.collaboration.ui.ActionLinkListener
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.isDefault
import com.intellij.collaboration.ui.SimpleComboboxWithActionsFactory
import com.intellij.collaboration.ui.codereview.BaseHtmlEditorPane
import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.util.bindDisabledIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.ui.util.name
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.asSafely
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

class RepositoryAndAccountSelectorComponentFactory<M : HostedGitRepositoryMapping, A : ServerAccount>(
  private val vm: RepositoryAndAccountSelectorViewModel<M, A>
) {
  fun create(
    scope: CoroutineScope,
    repoNamer: (M) -> @Nls String,
    detailsProvider: LoadingAccountsDetailsProvider<A, *>,
    accountsPopupActionsSupplier: (M) -> List<Action>,
    submitActionText: @Nls String,
    loginButtons: List<JButton>,
    errorPresenter: ErrorStatusPresenter<RepositoryAndAccountSelectorViewModel.Error>
  ): JComponent = create(scope,null, repoNamer, detailsProvider,accountsPopupActionsSupplier,submitActionText,loginButtons,errorPresenter)

  fun create(
    scope: CoroutineScope,
    defaultAccountHolder: DefaultAccountHolder<A>?,
    repoNamer: (M) -> @Nls String,
    detailsProvider: LoadingAccountsDetailsProvider<A, *>,
    accountsPopupActionsSupplier: (M) -> List<Action>,
    submitActionText: @Nls String,
    loginButtons: List<JButton>,
    errorPresenter: ErrorStatusPresenter<RepositoryAndAccountSelectorViewModel.Error>
  ): JComponent {
    val repoCombo = SimpleComboboxWithActionsFactory(vm.repositoriesState, vm.repoSelectionState).create(scope, { mapping ->
      SimpleComboboxWithActionsFactory.Presentation(
        repoNamer(mapping),
        mapping.remote.remote.name
      )
    }).apply {
      putClientProperty(PlatformDefaults.VISUAL_PADDING_PROPERTY, insets)
      bindDisabledIn(scope, vm.busyState)
    }

    val accountCombo = AccountSelectorComponentFactory(vm.accountsState, vm.accountSelectionState).create(
      scope,
      defaultAccountHolder,
      detailsProvider,
      Avatar.Sizes.BASE,
      Avatar.Sizes.ACCOUNT,
      CollaborationToolsBundle.message("account.choose.link"),
      vm.repoSelectionState.mapState(scope) { if (it == null) emptyList() else accountsPopupActionsSupplier(it) }
    ).apply {
      putClientProperty(PlatformDefaults.VISUAL_PADDING_PROPERTY, insets)
      bindDisabledIn(scope, vm.busyState)
    }

    val submitButton = JButton(submitActionText).apply {
      isDefault = true
      isOpaque = false

      addActionListener {
        vm.submitSelection()
      }

      bindVisibilityIn(scope, vm.submitAvailableState)
      bindDisabledIn(scope, vm.busyState)
    }

    val errorPanel = JPanel(BorderLayout()).apply {
      val iconPanel = JPanel(BorderLayout()).apply {
        val iconLabel = JLabel(AllIcons.Ide.FatalError)
        border = JBUI.Borders.emptyRight(iconLabel.iconTextGap)
        isOpaque = false
        add(iconLabel, BorderLayout.NORTH)
      }

      val errorTextPane = BaseHtmlEditorPane().apply htmlPane@{
        val actionLinkListener = ActionLinkListener(this@htmlPane)
        removeHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
        addHyperlinkListener(actionLinkListener)

        bindTextIn(scope, vm.errorState.map { error: RepositoryAndAccountSelectorViewModel.Error? ->
          if (error == null) return@map ""
          if (errorPresenter !is ErrorStatusPresenter.Text) {
            return@map error.asSafely<RepositoryAndAccountSelectorViewModel.Error.SubmissionError>()?.exception?.localizedMessage.orEmpty()
          }
          HtmlBuilder().append(errorPresenter.getErrorTitle(error)).br().apply {
            val errorDescription = errorPresenter.getErrorDescription(error)
            if (errorDescription != null) {
              append("$errorDescription ")
            }

            val errorAction = errorPresenter.getErrorAction(error)
            actionLinkListener.action = errorAction
            if (errorAction != null) {
              append(HtmlChunk.link(ActionLinkListener.ERROR_ACTION_HREF, errorAction.name.orEmpty()))
            }
          }.toString()
        })
      }

      isOpaque = false
      add(iconPanel, BorderLayout.WEST)
      add(errorTextPane, BorderLayout.CENTER)

      bindVisibilityIn(scope, vm.errorState.map { it != null })
    }
    val busyLabel = JLabel(AnimatedIcon.Default()).apply {
      bindVisibilityIn(scope, vm.busyState)
    }


    val actionsPanel = panel {
      row {
        cell(JPanel(HorizontalLayout(UI.scale(16))).apply {
          isOpaque = false
          add(submitButton)
          loginButtons.forEach {
            add(it)
          }
          add(busyLabel)

          putClientProperty(PlatformDefaults.VISUAL_PADDING_PROPERTY, submitButton.insets)
        })
      }

      AccountsPanelFactory.addWarningForMemoryOnlyPasswordSafeAndGet(
        scope,
        vm.canPersistCredentials,
        ::panel
      ).customize(UnscaledGaps(top = 5))
    }
    val labelTitle = CollaborationToolsBundle.message(if (ExperimentalUI.isNewUI()) "review.login.note.more" else "review.login.note.gear")

    return JPanel(null).apply {
      isOpaque = false
      border = JBUI.Borders.empty(30, 16)
      layout = MigLayout(LC().fill().gridGap("10", "16").insets("0").hideMode(3).noGrid())

      add(repoCombo, CC().growX().push())
      add(accountCombo, CC())

      add(actionsPanel, CC().newline())
      add(errorPanel, CC().newline())
      add(JLabel(labelTitle).apply {
        foreground = UIUtil.getContextHelpForeground()
      }, CC().newline().minWidth("0"))
    }
  }
}

