// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.CommonBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.util.ui.InlineIconButton
import icons.CollaborationToolsIcons
import org.jetbrains.plugins.github.i18n.GithubBundle
import java.awt.event.ActionListener
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

internal object GHTextActions {

  fun createEditButton(paneHandle: GHEditableHtmlPaneHandle): InlineIconButton {
    return createEditButton().apply {
      actionListener = ActionListener {
        paneHandle.showAndFocusEditor()
      }
    }
  }

  private fun createEditButton(): InlineIconButton {
    val icon = AllIcons.General.Inline_edit
    val hoverIcon = AllIcons.General.Inline_edit_hovered
    return InlineIconButton(icon, hoverIcon, tooltip = CommonBundle.message("button.edit"))
  }
}