// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import com.intellij.ui.ColoredListCellRenderer
import javax.swing.JList

internal class GitLabCloneListRenderer : ColoredListCellRenderer<GitLabCloneListItem>() {
  override fun customizeCellRenderer(list: JList<out GitLabCloneListItem>,
                                     value: GitLabCloneListItem,
                                     index: Int,
                                     selected: Boolean,
                                     hasFocus: Boolean) {
    clear()
    append(value.presentation())
  }
}