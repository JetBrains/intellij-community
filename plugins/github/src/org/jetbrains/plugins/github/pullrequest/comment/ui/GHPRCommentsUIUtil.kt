// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.EditorTextField
import com.intellij.ui.ListFocusTraversalPolicy
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.handleOnEdt
import java.util.concurrent.CompletableFuture
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

object GHPRCommentsUIUtil {

  fun createTogglableCommentField(project: Project, avatarIconsProvider: GHAvatarIconsProvider, author: GHUser,
                                  @Nls(capitalization = Nls.Capitalization.Title) actionName: String = "Comment",
                                  request: (String) -> CompletableFuture<*>): JComponent {
    val container = BorderLayoutPanel().andTransparent()
    val button = JButton(actionName + StringUtil.ELLIPSIS).apply {
      isOpaque = false
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }
    val buttonWrapper = Wrapper(button).apply {
      border = JBUI.Borders.emptyLeft(28)
    }
    button.addActionListener {
      with(container) {
        remove(buttonWrapper)
        val commentField = createCommentField(project, avatarIconsProvider, author, actionName, request)
        addToCenter(commentField)
        revalidate()
        repaint()
        GithubUIUtil.focusPanel(commentField)
      }
    }
    container.addToLeft(buttonWrapper)
    return container
  }

  fun createCommentField(project: Project, avatarIconsProvider: GHAvatarIconsProvider, author: GHUser,
                         @Nls(capitalization = Nls.Capitalization.Title) actionName: String = "Comment",
                         request: (String) -> CompletableFuture<*>): JComponent {

    val submitShortcut = CommonShortcuts.CTRL_ENTER
    val document = EditorFactory.getInstance().createDocument("")
    val textField = object : EditorTextField(document, project, FileTypes.PLAIN_TEXT) {
      //always paint pretty border
      override fun updateBorder(editor: EditorEx) = setupBorder(editor)

      override fun createEditor(): EditorEx {
        // otherwise border background is painted from multiple places
        return super.createEditor().apply {
          //TODO: fix in editor
          //com.intellij.openapi.editor.impl.EditorImpl.getComponent() == non-opaque JPanel
          // which uses default panel color
          component.isOpaque = false
          //com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder.paintBorder
          scrollPane.isOpaque = false
        }
      }
    }.apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
      setOneLineMode(false)
      setPlaceholder(actionName)
      addSettingsProvider {
        it.colorsScheme.lineSpacing = 1f
      }
    }

    val button = JButton(actionName).apply {
      isEnabled = false
      isOpaque = false
      toolTipText = KeymapUtil.getShortcutsText(submitShortcut.shortcuts)
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }
    document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        button.isEnabled = document.immutableCharSequence.isNotBlank()
      }
    })

    object : DumbAwareAction() {
      override fun actionPerformed(e: AnActionEvent) = submit(project, button, textField, request)
    }.registerCustomShortcutSet(submitShortcut, textField)
    button.addActionListener {
      submit(project, button, textField, request)
    }

    val authorLabel = LinkLabel.create("") {
      BrowserUtil.browse(author.url)
    }.apply {
      icon = avatarIconsProvider.getIcon(author.avatarUrl)
      isFocusable = true
      border = JBUI.Borders.empty(if (UIUtil.isUnderDarcula()) 4 else 2, 0)
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        textField.revalidate()
      }
    })

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
      add(button, CC().newline().skip().alignX("left"))
    }
  }

  private fun submit(project: Project, button: JButton, textField: EditorTextField,
                     request: (String) -> CompletableFuture<*>) {

    val document = textField.document
    if (!button.isEnabled || !textField.isEnabled || document.text.isBlank()) return
    textField.isEnabled = false
    button.isEnabled = false

    request(document.text).handleOnEdt { _, _ ->
      executeCommand(project) {
        runWriteAction { document.setText("") }
      }
      textField.isEnabled = true
      button.isEnabled = true
    }
  }
}