// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneRepositoriesListViewModel
import org.jetbrains.plugins.gitlab.ui.clone.model.GitLabCloneViewModel
import javax.swing.JList

internal class GitLabCloneListRenderer(
  private val cloneVm: GitLabCloneViewModel,
  private val vm: GitLabCloneRepositoriesListViewModel,
) : ColoredListCellRenderer<GitLabCloneListItem>() {
  override fun customizeCellRenderer(
    list: JList<out GitLabCloneListItem>,
    value: GitLabCloneListItem,
    index: Int,
    selected: Boolean,
    hasFocus: Boolean,
  ) {
    clear()
    when (value) {
      is GitLabCloneListItem.Error -> {
        val cloneError = value.error

        val action = swingAction(cloneError.name(), cloneError.performAction())

        append(cloneError.message(), SimpleTextAttributes.ERROR_ATTRIBUTES)
        append(" ")
        append(cloneError.name(), SimpleTextAttributes.LINK_ATTRIBUTES, action)
      }
      is GitLabCloneListItem.Repository -> append(value.presentation())
    }
  }

  private fun GitLabCloneException.message(): @Nls String = when (this) {
    is GitLabCloneException.ConnectionError -> CollaborationToolsBundle.message("error.connection.error")
    is GitLabCloneException.MissingAccessToken -> CollaborationToolsBundle.message("account.token.missing")
    is GitLabCloneException.RevokedToken -> CollaborationToolsBundle.message("http.status.error.refresh.token")
    is GitLabCloneException.Unknown -> message
  }

  private fun GitLabCloneException.name(): @Nls String = when (this) {
    is GitLabCloneException.MissingAccessToken,
    is GitLabCloneException.RevokedToken,
      -> CollaborationToolsBundle.message("login.again.action.text")
    is GitLabCloneException.ConnectionError,
    is GitLabCloneException.Unknown,
      -> CollaborationToolsBundle.message("clone.dialog.error.retry")
  }

  private fun GitLabCloneException.performAction(): (Any) -> Unit {
    when (this) {
      is GitLabCloneException.MissingAccessToken,
      is GitLabCloneException.RevokedToken,
        -> return { cloneVm.switchToLoginPanel(account) }
      is GitLabCloneException.ConnectionError,
      is GitLabCloneException.Unknown,
        -> return { vm.reload(account) }
    }
  }
}