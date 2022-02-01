// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.ui.codereview.timeline.comment.SubmittableTextFieldComponent
import com.intellij.collaboration.ui.codereview.timeline.comment.SubmittableTextFieldComponent.*
import com.intellij.collaboration.ui.codereview.timeline.comment.SubmittableTextFieldComponent.Companion.getEditorTextFieldVerticalOffset
import com.intellij.collaboration.ui.codereview.timeline.comment.SubmittableTextFieldModel
import com.intellij.collaboration.ui.codereview.timeline.comment.createSubmittableTextFieldComponent
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ListFocusTraversalPolicy
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class GHSubmittableTextFieldFactory(private val model: SubmittableTextFieldModel) {

  fun create(
    @NlsContexts.Tooltip actionName: String = GithubBundle.message("action.comment.text"),
    onCancel: (() -> Unit)? = null
  ): SubmittableTextFieldComponent {
    val submittableTextFieldConfig = Config(
      shouldScrollOnChange = true,
      submitConfig = SubmitActionConfig(ActionButtonConfig(actionName)),
      cancelConfig = onCancel?.let { CancelActionConfig(ActionButtonConfig(Messages.getCancelButton()), action = it) }
    )
    return createSubmittableTextFieldComponent(actionName, model, submittableTextFieldConfig)
  }

  fun create(
    avatarIconsProvider: GHAvatarIconsProvider, author: GHUser,
    @NlsContexts.Tooltip actionName: String = GithubBundle.message("action.comment.text"),
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

    val commentComponent = create(actionName, onCancel)
    return commentComponent.withAvatar(authorLabel)
  }

  fun SubmittableTextFieldComponent.withAvatar(
    authorLabel: JLabel
  ): JComponent {
    return JPanel(null).apply {
      isOpaque = false
      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fillX())
      isFocusCycleRoot = true
      isFocusTraversalPolicyProvider = true
      focusTraversalPolicy = ListFocusTraversalPolicy(listOf(this@withAvatar.submittableTextField, authorLabel))

      add(authorLabel, CC().alignY("top").gapRight("${JBUI.scale(6)}"))
      add(this@withAvatar, CC().grow().pushX())
    }
  }
}
