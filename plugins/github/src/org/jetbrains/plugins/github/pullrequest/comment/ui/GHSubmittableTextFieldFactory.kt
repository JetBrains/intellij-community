// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.ui.codereview.timeline.comment.SubmittableTextFieldComponent
import com.intellij.collaboration.ui.codereview.timeline.comment.SubmittableTextFieldComponent.ActionButtonConfig
import com.intellij.collaboration.ui.codereview.timeline.comment.SubmittableTextFieldComponent.CancelActionConfig
import com.intellij.collaboration.ui.codereview.timeline.comment.SubmittableTextFieldComponent.SubmitActionConfig
import com.intellij.collaboration.ui.codereview.timeline.comment.SubmittableTextFieldComponent.getEditorTextFieldVerticalOffset
import com.intellij.collaboration.ui.codereview.timeline.comment.SubmittableTextFieldFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.SubmittableTextFieldModel
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
  ): JComponent {
    val submittableTextField = SubmittableTextFieldFactory.create(model, actionName)
    return SubmittableTextFieldComponent.create(model, submittableTextField, createSubmittableTextFieldConfig(actionName, onCancel))
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

    val textField = SubmittableTextFieldFactory.create(model, actionName)
    val textFieldComponent = SubmittableTextFieldComponent.create(
      model, textField, createSubmittableTextFieldConfig(actionName, onCancel)
    )
    return wrapWithAvatar(textFieldComponent, textField, authorLabel)
  }

  private fun createSubmittableTextFieldConfig(
    @NlsContexts.Tooltip actionName: String,
    onCancel: (() -> Unit)? = null
  ): SubmittableTextFieldComponent.Config {
    return SubmittableTextFieldComponent.Config(
      submitConfig = SubmitActionConfig(ActionButtonConfig(actionName)),
      cancelConfig = onCancel?.let { CancelActionConfig(ActionButtonConfig(Messages.getCancelButton()), action = it) }
    )
  }

  private fun wrapWithAvatar(
    component: JComponent,
    commentComponent: JComponent,
    authorLabel: JLabel
  ): JComponent {
    return JPanel(null).apply {
      isOpaque = false
      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fillX())
      isFocusCycleRoot = true
      isFocusTraversalPolicyProvider = true
      focusTraversalPolicy = ListFocusTraversalPolicy(listOf(commentComponent, authorLabel))

      add(authorLabel, CC().alignY("top").gapRight("${JBUI.scale(6)}"))
      add(component, CC().grow().pushX())
    }
  }
}
