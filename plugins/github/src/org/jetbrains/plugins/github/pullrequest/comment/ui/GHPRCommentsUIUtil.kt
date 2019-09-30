// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.ListFocusTraversalPolicy
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.pullrequest.data.GHPRReviewServiceAdapter
import org.jetbrains.plugins.github.util.handleOnEdt
import org.jetbrains.plugins.github.util.successOnEdt
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

object GHPRCommentsUIUtil {

  fun createCommentField(project: Project, reviewService: GHPRReviewServiceAdapter, thread: GHPRReviewThreadModel,
                         @Nls(capitalization = Nls.Capitalization.Title) actionName: String = "Comment"): JComponent {

    val submitShortcut = CommonShortcuts.CTRL_ENTER
    val document = EditorFactory.getInstance().createDocument("")
    val textField = object : EditorTextField(document, project, FileTypes.PLAIN_TEXT) {
      //always paint pretty border
      override fun updateBorder(editor: EditorEx) = setupBorder(editor)

    }.apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
      setOneLineMode(false)
      setPlaceholder(actionName)
      addSettingsProvider {
        it.colorsScheme.lineSpacing = 1f
      }
    }.also {
      object : DumbAwareAction() {
        override fun actionPerformed(e: AnActionEvent) = submit(project, it, reviewService, thread)
      }.registerCustomShortcutSet(submitShortcut, it)
    }

    val button = JButton(actionName).apply {
      isOpaque = false
      addActionListener {
        submit(project, textField, reviewService, thread)
      }
      toolTipText = KeymapUtil.getShortcutsText(submitShortcut.shortcuts)
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    return JPanel().apply {
      isFocusCycleRoot = true
      isFocusTraversalPolicyProvider = true
      focusTraversalPolicy = ListFocusTraversalPolicy(listOf(textField, button, authorLabel))
      isOpaque = false
      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fillX())
      add(textField, CC().growX().pushX())
      add(button, CC().alignY("bottom"))
    }
  }

  private fun submit(project: Project, textField: EditorTextField, reviewService: GHPRReviewServiceAdapter, thread: GHPRReviewThreadModel) {
    val document = textField.document
    if (document.text.isBlank()) return
    textField.isEnabled = false

    reviewService.addComment(EmptyProgressIndicator(), document.text, thread.firstCommentDatabaseId)
      .successOnEdt {
        thread
          .addComment(GHPRReviewCommentModel(it.nodeId, it.createdAt, it.bodyHtml, it.user.login, it.user.htmlUrl, it.user.avatarUrl))
      }
      .handleOnEdt { _, _ ->
        executeCommand(project) {
          runWriteAction { document.setText("") }
        }
        textField.isEnabled = true
      }
  }
}