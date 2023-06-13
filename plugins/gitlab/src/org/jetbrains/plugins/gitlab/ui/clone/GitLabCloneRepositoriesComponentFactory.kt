// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.details.GroupedRenderer
import com.intellij.collaboration.ui.util.bindBusyIn
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount
import javax.swing.JComponent
import javax.swing.ListCellRenderer
import javax.swing.ListModel

internal object GitLabCloneRepositoriesComponentFactory {
  fun create(cs: CoroutineScope, cloneVm: GitLabCloneViewModel, searchField: SearchTextField): JComponent {
    val repositoryList = createRepositoryList(cs, cloneVm)
    CollaborationToolsUIUtil.attachSearch(repositoryList, searchField) { cloneItem ->
      cloneItem.presentation()
    }

    return panel {
      row {
        cell(searchField.textEditor)
          .resizableColumn()
          .align(Align.FILL)
      }
      row {
        scrollCell(repositoryList)
          .resizableColumn()
          .align(Align.FILL)
      }.resizableRow()
    }.apply {
      border = JBEmptyBorder(UIUtil.getRegularPanelInsets())
    }
  }

  private fun createRepositoryList(cs: CoroutineScope, cloneVm: GitLabCloneViewModel): JBList<GitLabCloneListItem> {
    val accountsModel = createAccountsModel(cs, cloneVm)
    val repositoriesModel = createRepositoriesModel(cs, cloneVm)
    return JBList(repositoriesModel).apply {
      cellRenderer = createRepositoryRenderer(accountsModel, repositoriesModel)
      isFocusable = false
      selectionModel.addListSelectionListener {
        cloneVm.selectItem(selectedValue)
      }
      bindBusyIn(cs, cloneVm.isLoading)
    }
  }

  private fun createAccountsModel(cs: CoroutineScope, cloneVm: GitLabCloneViewModel): ListModel<GitLabAccount> {
    val accountsModel = CollectionListModel<GitLabAccount>()
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      cloneVm.accounts.collectLatest { accounts ->
        accountsModel.replaceAll(accounts.toList())
      }
    }

    return accountsModel
  }

  private fun createRepositoriesModel(cs: CoroutineScope, cloneVm: GitLabCloneViewModel): ListModel<GitLabCloneListItem> {
    val accountsModel = CollectionListModel<GitLabCloneListItem>()
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      cloneVm.accounts.collectLatest { accounts ->
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
    accountsModel: ListModel<GitLabAccount>,
    repositoriesModel: ListModel<GitLabCloneListItem>
  ): ListCellRenderer<GitLabCloneListItem> {
    return GroupedRenderer(
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
}