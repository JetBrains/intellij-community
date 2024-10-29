// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.auth.ui.CompactAccountsPanelFactory
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.details.GroupedRenderer
import com.intellij.collaboration.ui.util.LinkActionMouseAdapter
import com.intellij.collaboration.ui.util.bindBusyIn
import com.intellij.dvcs.repo.ClonePathProvider
import com.intellij.dvcs.ui.CloneDvcsValidationUtils
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.dvcs.ui.FilePathDocumentChildPathHandle
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.cloneDialog.AccountMenuItem
import com.intellij.util.ui.cloneDialog.VcsCloneDialogUiSpec
import git4idea.GitUtil
import git4idea.remote.GitRememberedInputs
import git4idea.ui.GitShallowCloneComponentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneRepositoriesViewModel
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneRepositoriesViewModel.SearchModel
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneViewModel
import javax.swing.JComponent
import javax.swing.JSeparator
import javax.swing.ListCellRenderer
import javax.swing.ListModel
import javax.swing.event.DocumentEvent

internal object GitLabCloneRepositoriesComponentFactory {
  fun create(
    project: Project,
    cs: CoroutineScope,
    repositoriesVm: GitLabCloneRepositoriesViewModel,
    cloneVm: GitLabCloneViewModel
  ): DialogPanel {
    val searchField = createSearchField(repositoriesVm)
    val directoryField = createDirectoryField(project, cs, repositoriesVm)

    val accountsModel = createAccountsModel(cs, repositoriesVm)
    val repositoriesModel = createRepositoriesModel(cs, repositoriesVm)

    val accountsPanel = CompactAccountsPanelFactory(accountsModel).create(
      repositoriesVm.accountDetailsProvider,
      VcsCloneDialogUiSpec.Components.avatarSize,
      AccountsPopupConfig(cloneVm)
    )
    val repositoryList = createRepositoryList(cs, repositoriesVm, accountsModel, repositoriesModel)
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
            CloneDvcsValidationUtils.checkDirectory(it.text, it.textField as JComponent)
          }
      }
      if (Registry.`is`("git.clone.shallow")) {
        GitShallowCloneComponentFactory.appendShallowCloneRow(this, repositoriesVm.shallowCloneVm)
      }
    }.apply {
      border = JBEmptyBorder(UIUtil.getRegularPanelInsets())
    }
  }

  private fun createRepositoryList(
    cs: CoroutineScope,
    repositoriesVm: GitLabCloneRepositoriesViewModel,
    accountsModel: ListModel<GitLabAccount>,
    repositoriesModel: ListModel<GitLabCloneListItem>
  ): JBList<GitLabCloneListItem> {
    return JBList(repositoriesModel).apply {
      cellRenderer = createRepositoryRenderer(accountsModel, repositoriesModel)
      isFocusable = false
      selectionModel.addListSelectionListener {
        repositoriesVm.selectItem(selectedValue)
      }
      bindBusyIn(cs, repositoriesVm.isLoading)

      val mouseAdapter = LinkActionMouseAdapter(this)
      addMouseListener(mouseAdapter)
      addMouseMotionListener(mouseAdapter)

      cs.launchNow {
        repositoriesVm.searchValue.collectLatest { searchValue ->
          emptyText.text = when (searchValue) {
            is SearchModel.Text -> StatusText.getDefaultEmptyText()
            is SearchModel.Url -> CollaborationToolsBundle.message("clone.dialog.repository.url.text", searchValue.url)
          }
        }
      }
    }
  }

  private fun createAccountsModel(cs: CoroutineScope, repositoriesVm: GitLabCloneRepositoriesViewModel): ListModel<GitLabAccount> {
    val accountsModel = CollectionListModel<GitLabAccount>()
    cs.launch {
      repositoriesVm.accountsUpdatedRequest.collectLatest { accounts ->
        accountsModel.replaceAll(accounts.toList())
      }
    }

    return accountsModel
  }

  private fun createRepositoriesModel(
    cs: CoroutineScope,
    repositoriesVm: GitLabCloneRepositoriesViewModel
  ): ListModel<GitLabCloneListItem> {
    val repositoriesModel = CollectionListModel<GitLabCloneListItem>()
    cs.launch {
      repositoriesVm.items.collectLatest { items ->
        repositoriesModel.replaceAll(items)
      }
    }

    return repositoriesModel
  }

  private fun createRepositoryRenderer(
    accountsModel: ListModel<GitLabAccount>,
    repositoriesModel: ListModel<GitLabCloneListItem>
  ): ListCellRenderer<GitLabCloneListItem> {
    return GroupedRenderer.create(
      baseRenderer = GitLabCloneListRenderer(),
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

  private fun createSearchField(repositoriesVm: GitLabCloneRepositoriesViewModel): SearchTextField {
    return SearchTextField(false).apply {
      addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          repositoriesVm.setSearchValue(text)
        }
      })
    }
  }

  private fun createDirectoryField(
    project: Project,
    cs: CoroutineScope,
    repositoriesVm: GitLabCloneRepositoriesViewModel
  ): TextFieldWithBrowseButton {
    val directoryField = TextFieldWithBrowseButton().apply {
      addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor()
        .withShowFileSystemRoots(true)
        .withHideIgnored(false)
        .withTitle(DvcsBundle.message("clone.destination.directory.browser.title"))
        .withDescription(DvcsBundle.message("clone.destination.directory.browser.description"))
      )
      addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          repositoriesVm.setDirectoryPath(text)
        }
      })
    }
    val cloneDirectoryChildHandle = FilePathDocumentChildPathHandle.install(
      directoryField.textField.document,
      ClonePathProvider.defaultParentDirectoryPath(project, GitRememberedInputs.getInstance())
    )

    cs.launchNow {
      repositoriesVm.selectedUrl.filterNotNull().collectLatest { selectedUrl ->
        val path = ClonePathProvider.relativeDirectoryPathForVcsUrl(project, selectedUrl).removeSuffix(GitUtil.DOT_GIT)
        cloneDirectoryChildHandle.trySetChildPath(path)
      }
    }

    return directoryField
  }

  private class AccountsPopupConfig(cloneVm: GitLabCloneViewModel) : CompactAccountsPanelFactory.PopupConfig<GitLabAccount> {
    private val loginWithTokenAction: AccountMenuItem.Action = AccountMenuItem.Action(
      CollaborationToolsBundle.message("clone.dialog.login.with.token.action"),
      { cloneVm.switchToLoginPanel(account = null) },
      showSeparatorAbove = true
    )

    override val avatarSize: Int = VcsCloneDialogUiSpec.Components.popupMenuAvatarSize

    override fun createActions(): Collection<AccountMenuItem.Action> = listOf(loginWithTokenAction)
  }
}
