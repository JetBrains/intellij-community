// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.collaboration.ui.util.name
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JList

internal class GitLabCloneListRenderer : ColoredListCellRenderer<GitLabCloneListItem>() {
  override fun customizeCellRenderer(list: JList<out GitLabCloneListItem>,
                                     value: GitLabCloneListItem,
                                     index: Int,
                                     selected: Boolean,
                                     hasFocus: Boolean) {
    clear()
    when (value) {
      is GitLabCloneListItem.Error -> {
        val cloneError = value.error
        val errorActionConfig = cloneError.errorActionConfig
        val action = swingAction(errorActionConfig.name) { errorActionConfig.action() }

        append(cloneError.message, SimpleTextAttributes.ERROR_ATTRIBUTES)
        append(" ")
        append(action.name.orEmpty(), SimpleTextAttributes.LINK_ATTRIBUTES, action)
      }
      is GitLabCloneListItem.Repository -> append(value.presentation())
    }
  }
}