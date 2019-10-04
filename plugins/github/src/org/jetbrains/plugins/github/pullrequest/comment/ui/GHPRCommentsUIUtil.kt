// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.ListFocusTraversalPolicy
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.util.handleOnEdt
import java.util.concurrent.CompletableFuture
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

object GHPRCommentsUIUtil {

  fun createCommentField(project: Project, avatarIconsProvider: GHAvatarIconsProvider, author: GHUser,
                         @Nls(capitalization = Nls.Capitalization.Title) actionName: String = "Comment",
                         request: (String) -> CompletableFuture<*>): JComponent {

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
        override fun actionPerformed(e: AnActionEvent) = submit(project, it, request)
      }.registerCustomShortcutSet(submitShortcut, it)
    }

    val button = JButton(actionName).apply {
      isOpaque = false
      addActionListener {
        submit(project, textField, request)
      }
      toolTipText = KeymapUtil.getShortcutsText(submitShortcut.shortcuts)
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    val authorLabel = LinkLabel.create("") {
      BrowserUtil.browse(author.url)
    }.apply {
      icon = avatarIconsProvider.getIcon(author.avatarUrl)
      isFocusable = true
      border = JBUI.Borders.empty(2, 0)
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    return JPanel().apply {
      isFocusCycleRoot = true
      isFocusTraversalPolicyProvider = true
      focusTraversalPolicy = ListFocusTraversalPolicy(listOf(textField, button, authorLabel))
      isOpaque = false
      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fillX()).apply {
        columnConstraints = "[]${UI.scale(8)}[]${UI.scale(4)}[]"
      }
      add(authorLabel, CC().alignY("top"))
      add(textField, CC().growX().pushX())
      add(button, CC().alignY("bottom"))
    }
  }

  private fun submit(project: Project, textField: EditorTextField, request: (String) -> CompletableFuture<*>) {
    val document = textField.document
    if (document.text.isBlank()) return
    textField.isEnabled = false

    request(document.text).handleOnEdt { _, _ ->
      executeCommand(project) {
        runWriteAction { document.setText("") }
      }
      textField.isEnabled = true
    }
  }
}