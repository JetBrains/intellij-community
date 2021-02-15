// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.component

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.ui.util.getName
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import javax.swing.JList

class GHRepositorySelectorComponentFactory {
  fun create(model: ComboBoxWithActionsModel<GHGitRepositoryMapping>): ComboBox<*> {
    return ComboBox(model).apply {
      renderer = object : ColoredListCellRenderer<ComboBoxWithActionsModel.Item<GHGitRepositoryMapping>>() {
        override fun customizeCellRenderer(list: JList<out ComboBoxWithActionsModel.Item<GHGitRepositoryMapping>>,
                                           value: ComboBoxWithActionsModel.Item<GHGitRepositoryMapping>?,
                                           index: Int,
                                           selected: Boolean,
                                           hasFocus: Boolean) {
          if (value is ComboBoxWithActionsModel.Item.Wrapper) {
            val mapping = value.wrappee.castSafelyTo<GHGitRepositoryMapping>() ?: return
            val repositoryName = GHUIUtil.getRepositoryDisplayName(model.items.map(GHGitRepositoryMapping::repository),
                                                                   mapping.repository,
                                                                   true)
            val remoteName = mapping.gitRemote.remote.name
            append(repositoryName).append(" ").append(remoteName, SimpleTextAttributes.GRAYED_ATTRIBUTES)
          }
          if (value is ComboBoxWithActionsModel.Item.Action) {
            if (model.size == index) border = IdeBorderFactory.createBorder(SideBorder.TOP)
            append(value.action.getName())
          }
        }
      }
      isUsePreferredSizeAsMinimum = false
      isOpaque = false
      isSwingPopup = true
    }
  }
}