// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.icons.AllIcons
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
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.EditorTextField
import com.intellij.ui.ListFocusTraversalPolicy
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import icons.GithubIcons
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.ui.InlineIconButton
import org.jetbrains.plugins.github.util.handleOnEdt
import java.awt.Dimension
import java.awt.event.*
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
import kotlin.properties.Delegates

object GHPRSubmittableTextField {

  private val SUBMIT_SHORTCUT_SET = CommonShortcuts.CTRL_ENTER
  private val CANCEL_SHORTCUT_SET = CommonShortcuts.ESCAPE

  fun create(model: Model, avatarIconsProvider: GHAvatarIconsProvider, author: GHUser,
             @Nls(capitalization = Nls.Capitalization.Title) actionName: String = "Comment",
             onCancel: (() -> Unit)? = null): JComponent {

    val textField = object : EditorTextField(model.document, null, FileTypes.PLAIN_TEXT) {
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
      selectAll()
    }

    val submitButton =
      InlineIconButton(GithubIcons.Send, GithubIcons.SendHovered, tooltip = actionName, shortcut = SUBMIT_SHORTCUT_SET).apply {
        putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
      }

    val cancelButton =
      InlineIconButton(AllIcons.Actions.Close, AllIcons.Actions.CloseHovered, tooltip = "Cancel", shortcut = CANCEL_SHORTCUT_SET).apply {
        border = JBUI.Borders.empty(if (UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) 6 else 4, 0)
        putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
      }

    val authorLabel = LinkLabel.create("") {
      BrowserUtil.browse(author.url)
    }.apply {
      icon = avatarIconsProvider.getIcon(author.avatarUrl)
      isFocusable = true
      border = JBUI.Borders.empty(if (UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) 4 else 2, 0)
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    Controller(model, textField, submitButton, cancelButton, onCancel)

    val panel = JPanel(null)
    textField.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        panel.revalidate()
      }
    })

    val textFieldWithSubmitButton = createTextFieldWithInlinedButton(textField, submitButton)
    return panel.apply {
      isFocusCycleRoot = true
      isFocusTraversalPolicyProvider = true
      focusTraversalPolicy = ListFocusTraversalPolicy(listOf(textField, authorLabel))
      isOpaque = false
      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fillX(),
                         AC().gap("${UI.scale(6)}"))
      add(authorLabel, CC().alignY("top"))
      add(textFieldWithSubmitButton, CC().grow().pushX())
      add(cancelButton, CC().alignY("top").hideMode(3))
    }
  }

  private fun createTextFieldWithInlinedButton(textField: EditorTextField, button: JComponent): JComponent {

    val bordersListener = object : ComponentAdapter(), HierarchyListener {
      override fun componentResized(e: ComponentEvent?) {
        val scrollPane = (textField.editor as? EditorEx)?.scrollPane ?: return
        val buttonSize = button.size
        JBInsets.removeFrom(buttonSize, button.insets)
        scrollPane.viewportBorder = JBUI.Borders.emptyRight(buttonSize.width)
        scrollPane.viewport.revalidate()
      }

      override fun hierarchyChanged(e: HierarchyEvent?) {
        val scrollPane = (textField.editor as? EditorEx)?.scrollPane ?: return
        button.border = EmptyBorder(scrollPane.border.getBorderInsets(scrollPane))
        componentResized(null)
      }
    }

    textField.addHierarchyListener(bordersListener)
    button.addComponentListener(bordersListener)

    val layeredPane = object : JLayeredPane() {
      override fun getPreferredSize(): Dimension {
        return textField.preferredSize
      }

      override fun doLayout() {
        super.doLayout()
        textField.setBounds(0, 0, width, height)
        val preferredButtonSize = button.preferredSize
        button.setBounds(width - preferredButtonSize.width, height - preferredButtonSize.height,
                         preferredButtonSize.width, preferredButtonSize.height)
      }
    }
    layeredPane.add(textField, JLayeredPane.DEFAULT_LAYER, 0)
    layeredPane.add(button, JLayeredPane.POPUP_LAYER, 1)

    return layeredPane
  }

  class Model(private val submitter: (String) -> CompletableFuture<*>) {
    val document = EditorFactory.getInstance().createDocument("")

    var isSubmitting by Delegates.observable(false) { _, _, _ ->
      stateEventDispatcher.multicaster.eventOccurred()
    }
      private set

    private val stateEventDispatcher = EventDispatcher.create(SimpleEventListener::class.java)

    fun submit() {
      if (isSubmitting) return

      isSubmitting = true
      submitter(document.text).handleOnEdt { _, _ ->
        executeCommand {
          runWriteAction {
            document.setText("")
          }
        }
        isSubmitting = false
      }
    }

    fun addStateListener(listener: () -> Unit) = stateEventDispatcher.addListener(object : SimpleEventListener {
      override fun eventOccurred() = listener()
    })
  }

  private class Controller(private val model: Model,
                           private val textField: EditorTextField,
                           private val submitButton: InlineIconButton,
                           cancelButton: InlineIconButton,
                           onCancel: (() -> Unit)?) {
    init {
      textField.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          updateSubmittionState()
        }
      })

      submitButton.actionListener = ActionListener { model.submit() }

      object : DumbAwareAction() {
        override fun actionPerformed(e: AnActionEvent) = model.submit()
      }.registerCustomShortcutSet(SUBMIT_SHORTCUT_SET, textField)

      cancelButton.isVisible = onCancel != null
      if (onCancel != null) {
        cancelButton.actionListener = ActionListener { onCancel() }

        object : DumbAwareAction() {
          override fun actionPerformed(e: AnActionEvent) {
            onCancel()
          }
        }.registerCustomShortcutSet(CANCEL_SHORTCUT_SET, textField)
      }

      model.addStateListener {
        updateSubmittionState()
      }
      updateSubmittionState()
    }

    private fun updateSubmittionState() {
      textField.isEnabled = !model.isSubmitting
      submitButton.isEnabled = !model.isSubmitting && textField.text.isNotBlank()
    }
  }
}