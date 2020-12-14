// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.util.ui.codereview.InlineIconButton
import icons.VcsCodeReviewIcons
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.event.ActionListener
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

internal object GHTextActions {
  fun createDeleteButton(delete: () -> CompletableFuture<out Any?>): JComponent {
    val icon = VcsCodeReviewIcons.Delete
    val hoverIcon = VcsCodeReviewIcons.DeleteHovered
    val button = InlineIconButton(icon, hoverIcon, tooltip = CommonBundle.message("button.delete"))
    button.actionListener = ActionListener {
      if (MessageDialogBuilder.yesNo(GithubBundle.message("pull.request.review.comment.delete.dialog.title"),
                                     GithubBundle.message("pull.request.review.comment.delete.dialog.msg")).ask(button)) {
        delete()
      }
    }
    return button
  }

  fun createEditButton(paneHandle: GHEditableHtmlPaneHandle): JComponent {
    val icon = AllIcons.General.Inline_edit
    val hoverIcon = AllIcons.General.Inline_edit_hovered
    val button = InlineIconButton(icon, hoverIcon, tooltip = CommonBundle.message("button.edit"))
    button.actionListener = ActionListener {
      paneHandle.showAndFocusEditor()
    }
    return button
  }
}