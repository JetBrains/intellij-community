// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.Messages
import icons.GithubIcons
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.InlineIconButton
import java.awt.event.ActionListener
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

internal object GHTextActions {

  fun createDeleteButton(delete: () -> CompletableFuture<out Any?>): JComponent {
    val icon = GithubIcons.Delete
    val hoverIcon = GithubIcons.DeleteHovered
    return InlineIconButton(icon, hoverIcon, tooltip = CommonBundle.message("button.delete")).apply {
      actionListener = ActionListener {
        if (Messages.showConfirmationDialog(this, GithubBundle.message("pull.request.review.comment.delete.dialog.msg"),
                                            GithubBundle.message("pull.request.review.comment.delete.dialog.title"),
                                            Messages.getYesButton(), Messages.getNoButton()) == Messages.YES) {
          delete()
        }
      }
    }
  }

  fun createEditButton(paneHandle: GHEditableHtmlPaneHandle): JComponent {
    val action = ActionListener {
      paneHandle.showAndFocusEditor()
    }
    val icon = AllIcons.General.Inline_edit
    val hoverIcon = AllIcons.General.Inline_edit_hovered
    return InlineIconButton(icon, hoverIcon, tooltip = CommonBundle.message("button.edit")).apply {
      actionListener = action
    }
  }
}