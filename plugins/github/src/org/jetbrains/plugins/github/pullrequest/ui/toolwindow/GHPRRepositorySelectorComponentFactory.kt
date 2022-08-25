// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.ui.CollaborationToolsUIUtil.defaultButton
import com.intellij.collaboration.ui.ComboBoxWithActionsModel
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.layout.PlatformDefaults
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.component.GHAccountSelectorComponentFactory
import org.jetbrains.plugins.github.ui.component.GHRepositorySelectorComponentFactory
import org.jetbrains.plugins.github.ui.util.getName
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class GHPRRepositorySelectorComponentFactory(private val vm: GHPRRepositorySelectorViewModel) {

  fun create(scope: CoroutineScope): JComponent {
    val repositoriesModel = ComboBoxWithActionsModel<GHGitRepositoryMapping>().apply {
      //todo: add remote actions
      sync(scope, vm.repositoriesState, vm.repoSelectionState)
    }

    val accountsModel = ComboBoxWithActionsModel<GithubAccount>().apply {
      sync(scope, vm.accountsState, vm.accountSelectionState)
    }

    scope.launch {
      vm.repoSelectionState.map { repo ->
        createPopupLoginActions(repo)
      }.collect {
        accountsModel.actions = it
      }
    }

    val applyAction = object : AbstractAction(GithubBundle.message("pull.request.view.list")) {
      override fun actionPerformed(e: ActionEvent?) {
        vm.trySubmitSelection()
      }
    }
    val githubLoginAction = object : AbstractAction(GithubBundle.message("action.Github.Accounts.AddGHAccount.text")) {
      override fun actionPerformed(e: ActionEvent?) {
        vm.loginToGithub()?.run { vm.trySubmitSelection() }
      }
    }
    val tokenLoginAction = object : AbstractAction(GithubBundle.message("action.Github.Accounts.AddGHAccountWithToken.text")) {
      override fun actionPerformed(e: ActionEvent?) {
        vm.loginToGithub(false)?.run { vm.trySubmitSelection() }
      }
    }
    val gheLoginAction = object : AbstractAction(GithubBundle.message("action.Github.Accounts.AddGHEAccount.text")) {
      override fun actionPerformed(e: ActionEvent?) {
        vm.loginToGhe()?.run { vm.trySubmitSelection() }
      }
    }

    scope.launch {
      combine(vm.accountsState, vm.repoSelectionState) { accounts, repo ->
        accounts.isNotEmpty() to (repo?.repository?.serverPath?.isGithubDotCom ?: false)
      }.collect { (hasAccounts, isDotCom) ->
        applyAction.visible = hasAccounts
        githubLoginAction.visible = !hasAccounts && isDotCom
        tokenLoginAction.visible = !hasAccounts && isDotCom
        gheLoginAction.visible = !hasAccounts && !isDotCom
      }
    }

    scope.launch {
      combine(vm.repoSelectionState, vm.accountSelectionState) { repo, account ->
        repo != null && account != null
      }.collect {
        applyAction.isEnabled = it
      }
    }


    val repoCombo = GHRepositorySelectorComponentFactory().create(repositoriesModel).apply {
      putClientProperty(PlatformDefaults.VISUAL_PADDING_PROPERTY, insets)
    }
    val accountCombo = GHAccountSelectorComponentFactory().create(accountsModel).apply {
      putClientProperty(PlatformDefaults.VISUAL_PADDING_PROPERTY, insets)
    }

    val applyButton = JButton(applyAction).defaultButton().apply {
      isOpaque = false
      controlVisibilityFromAction(this, applyAction)
    }

    val githubLoginButton = JButton(githubLoginAction).defaultButton().apply {
      isOpaque = false
      controlVisibilityFromAction(this, githubLoginAction)
    }
    val tokenLoginLink = createLinkLabel(tokenLoginAction)
    val gheLoginButton = JButton(gheLoginAction).defaultButton().apply {
      isOpaque = false
      controlVisibilityFromAction(this, gheLoginAction)
    }
    val actionsPanel = JPanel(HorizontalLayout(UI.scale(16))).apply {
      isOpaque = false
      add(applyButton)
      add(githubLoginButton)
      add(tokenLoginLink)
      add(gheLoginButton)

      putClientProperty(PlatformDefaults.VISUAL_PADDING_PROPERTY, applyButton.insets)
    }

    return JPanel(null).apply {
      isOpaque = false
      border = JBUI.Borders.empty(30, 16)
      layout = MigLayout(LC().fill().gridGap("${UI.scale(10)}px", "${UI.scale(16)}px").insets("0").hideMode(3).noGrid())

      add(repoCombo, CC().growX().push())
      add(accountCombo, CC())

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
          vm.loginToGithub()?.let { vm.accountSelectionState.value = it }
        }
      }, object : AbstractAction(GithubBundle.message("action.Github.Accounts.AddGHAccountWithToken.text")) {
        override fun actionPerformed(e: ActionEvent?) {
          vm.loginToGithub(false)?.let { vm.accountSelectionState.value = it }
        }
      })
    else listOf(
      object : AbstractAction(GithubBundle.message("action.Github.Accounts.AddGHEAccount.text")) {
        override fun actionPerformed(e: ActionEvent?) {
          vm.loginToGhe()?.let { vm.accountSelectionState.value = it }
        }
      })
  }

  companion object {
    private const val ACTION_VISIBLE_KEY = "ACTION_VISIBLE"

    private fun controlVisibilityFromAction(button: JButton, action: Action) {
      fun update() {
        button.isVisible = action.getValue(ACTION_VISIBLE_KEY) as? Boolean ?: true
      }
      action.addPropertyChangeListener {
        update()
      }
      update()
    }

    private var Action.visible: Boolean
      get() = getValue(ACTION_VISIBLE_KEY) as? Boolean ?: true
      set(value) = putValue(ACTION_VISIBLE_KEY, value)

    private fun createLinkLabel(action: Action): ActionLink {
      val label = ActionLink(action.getName()) {
        action.actionPerformed(it)
      }
      label.isEnabled = action.isEnabled
      label.isVisible = action.getValue(ACTION_VISIBLE_KEY) as? Boolean ?: true

      action.addPropertyChangeListener {
        label.text = action.getName()
        label.isEnabled = action.isEnabled
        label.isVisible = action.getValue(ACTION_VISIBLE_KEY) as? Boolean ?: true
      }
      return label
    }

    private fun <T> ComboBoxModel<T>.addSelectionChangeListener(listener: () -> Unit) {
      addListDataListener(object : ListDataListener {
        override fun contentsChanged(e: ListDataEvent) {
          if (e.index0 == -1 && e.index1 == -1) listener()
        }

        override fun intervalAdded(e: ListDataEvent) {}
        override fun intervalRemoved(e: ListDataEvent) {}
      })
    }

    private fun <T : Any> ComboBoxWithActionsModel<T>.sync(scope: CoroutineScope,
                                                           listState: StateFlow<List<T>>,
                                                           selectionState: MutableStateFlow<T?>) {
      scope.launch {
        listState.collect {
          items = it
        }
      }
      addSelectionChangeListener {
        selectionState.value = selectedItem?.wrappee
      }
      scope.launch {
        selectionState.collect { item ->
          if (selectedItem?.wrappee != item) {
            selectedItem = item?.let { ComboBoxWithActionsModel.Item.Wrapper(it) }
          }
        }
      }
    }
  }
}
