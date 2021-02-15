// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextField
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextField.Companion.getEditorTextFieldVerticalOffset
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextFieldModel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import javax.swing.JComponent

class GHSubmittableTextFieldFactory(private val model: SubmittableTextFieldModel) {

  fun create(
    @NlsActions.ActionText actionName: String = GithubBundle.message("action.comment.text"),
    onCancel: (() -> Unit)? = null
  ): JComponent = SubmittableTextField(actionName, model, onCancel = onCancel)

  fun create(
    avatarIconsProvider: GHAvatarIconsProvider, author: GHUser,
    @NlsActions.ActionText actionName: String = GithubBundle.message("action.comment.text"),
    onCancel: (() -> Unit)? = null
  ): JComponent {
    val authorLabel = LinkLabel.create("") {
      BrowserUtil.browse(author.url)
    }.apply {
      icon = avatarIconsProvider.getIcon(author.avatarUrl)
      isFocusable = true
      border = JBUI.Borders.empty(getEditorTextFieldVerticalOffset() - 2, 0)
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    return SubmittableTextField(actionName, model, authorLabel, onCancel)
  }
}
