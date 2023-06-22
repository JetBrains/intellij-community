// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.auth.ui.CompactAccountsPanelFactory
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.details.GroupedRenderer
import com.intellij.collaboration.ui.util.LinkActionMouseAdapter
import com.intellij.collaboration.ui.util.bindBusyIn
import com.intellij.dvcs.ui.CloneDvcsValidationUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.cloneDialog.AccountMenuItem
import com.intellij.util.ui.cloneDialog.VcsCloneDialogUiSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.exception.GitLabHttpStatusErrorAction
import javax.swing.JSeparator
import javax.swing.ListCellRenderer
import javax.swing.ListModel

internal object GitLabCloneRepositoriesComponentFactory {
  fun create(
    project: Project,
    cs: CoroutineScope,
    cloneVm: GitLabCloneViewModel,
    searchField: SearchTextField,
    directoryField: TextFieldWithBrowseButton
  ): DialogPanel {
    val accountsModel = createAccountsModel(cs, cloneVm)
    val repositoriesModel = createRepositoriesModel(cs, cloneVm)

    val accountsPanel = CompactAccountsPanelFactory(accountsModel).create(
      cloneVm.accountDetailsProvider,
      VcsCloneDialogUiSpec.Components.avatarSize,
      AccountsPopupConfig(cloneVm)
    )
    val repositoryList = createRepositoryList(project, cs, cloneVm, accountsModel, repositoriesModel)
    CollaborationToolsUIUtil.attachSearch(repositoryList, searchField) { cloneItem ->
      when (cloneItem) {
        is GitLabCloneListItem.Error -> ""
        is GitLabCloneListItem.Repository -> cloneItem.presentation()
      }
    }

    return panel {
      row {
        cell(searchField.textEditor)
          .resizableColumn()
          .align(Align.FILL)
        cell(JSeparator(JSeparator.VERTICAL))
          .align(AlignY.FILL)
        cell(accountsPanel)
          .align(AlignY.FILL)
      }
      row {
        scrollCell(repositoryList)
          .resizableColumn()
          .align(Align.FILL)
      }.resizableRow()
      row(CollaborationToolsBundle.message("clone.dialog.directory.to.clone.label.text")) {
        cell(directoryField)
          .align(AlignX.FILL)
          .validationOnApply {
            CloneDvcsValidationUtils.checkDirectory(it.text, it.textField)
          }
      }
    }.apply {
      border = JBEmptyBorder(UIUtil.getRegularPanelInsets())
    }
  }

  private fun createRepositoryList(
    project: Project,
    cs: CoroutineScope,
    cloneVm: GitLabCloneViewModel,
    accountsModel: ListModel<GitLabAccount>,
    repositoriesModel: ListModel<GitLabCloneListItem>
  ): JBList<GitLabCloneListItem> {
    return JBList(repositoriesModel).apply {
      cellRenderer = createRepositoryRenderer(project, cs, cloneVm.accountManager, accountsModel, repositoriesModel)
      isFocusable = false
      selectionModel.addListSelectionListener {
        cloneVm.selectItem(selectedValue)
      }
      bindBusyIn(cs, cloneVm.isLoading)

      val mouseAdapter = LinkActionMouseAdapter(this)
      addMouseListener(mouseAdapter)
      addMouseMotionListener(mouseAdapter)
    }
  }

  private fun createAccountsModel(cs: CoroutineScope, cloneVm: GitLabCloneViewModel): ListModel<GitLabAccount> {
    val accountsModel = CollectionListModel<GitLabAccount>()
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      cloneVm.accountsRefreshRequest.collectLatest { accounts ->
        accountsModel.replaceAll(accounts.toList())
      }
    }

    return accountsModel
  }

  private fun createRepositoriesModel(cs: CoroutineScope, cloneVm: GitLabCloneViewModel): ListModel<GitLabCloneListItem> {
    val accountsModel = CollectionListModel<GitLabCloneListItem>()
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      cloneVm.accountsRefreshRequest.collectLatest { accounts ->
        cloneVm.runTask {
          val repositories = accounts.flatMap { account ->
            cloneVm.collectAccountRepositories(account)
          }
          accountsModel.replaceAll(repositories)
        }
      }
    }

    return accountsModel
  }

  private fun createRepositoryRenderer(
    project: Project,
    cs: CoroutineScope,
    accountManager: GitLabAccountManager,
    accountsModel: ListModel<GitLabAccount>,
    repositoriesModel: ListModel<GitLabCloneListItem>
  ): ListCellRenderer<GitLabCloneListItem> {
    return GroupedRenderer(
      baseRenderer = GitLabCloneListRenderer { account -> GitLabHttpStatusErrorAction.LogInAgain(project, cs, account, accountManager) },
      hasSeparatorAbove = { value, index ->
        when (index) {
          0 -> accountsModel.size > 1
          else -> {
            val previousAccount = repositoriesModel.getElementAt(index - 1).account
            previousAccount != value.account
          }
        }
      },
      buildSeparator = { value, index, _ ->
        GroupedRenderer.createDefaultSeparator(text = value.account.name, paintLine = index != 0)
      }
    )
  }

  private class AccountsPopupConfig(cloneVm: GitLabCloneViewModel) : CompactAccountsPanelFactory.PopupConfig<GitLabAccount> {
    private val loginWithTokenAction: AccountMenuItem.Action = AccountMenuItem.Action(
      CollaborationToolsBundle.message("clone.dialog.login.with.token.action"),
      { cloneVm.switchToLoginPanel() },
      showSeparatorAbove = true
    )

    override val avatarSize: Int = VcsCloneDialogUiSpec.Components.popupMenuAvatarSize

    override fun createActions(): Collection<AccountMenuItem.Action> = listOf(loginWithTokenAction)
  }
}