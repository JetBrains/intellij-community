// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.editor.actions.IncrementalFindAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorTextField
import com.intellij.ui.ListFocusTraversalPolicy
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.*
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import icons.GithubIcons
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.InlineIconButton
import java.awt.Dimension
import java.awt.event.*
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

class GHSubmittableTextFieldFactory(private val model: GHSubmittableTextFieldModel) {

  companion object {
    private val SUBMIT_SHORTCUT_SET = CommonShortcuts.CTRL_ENTER
    private val CANCEL_SHORTCUT_SET = CommonShortcuts.ESCAPE
  }

  fun create(@Nls(capitalization = Nls.Capitalization.Title) actionName: String = "Comment",
             onCancel: (() -> Unit)? = null): JComponent {

    val textField = createTextField(actionName)
    val submitButton = createSubmitButton(actionName)
    val cancelButton = createCancelButton()

    return create(textField, submitButton, cancelButton, null, onCancel)
  }

  fun create(avatarIconsProvider: GHAvatarIconsProvider, author: GHUser,
             @Nls(capitalization = Nls.Capitalization.Title) actionName: String = "Comment",
             onCancel: (() -> Unit)? = null): JComponent {

    val textField = createTextField(actionName)
    val submitButton = createSubmitButton(actionName)
    val cancelButton = createCancelButton()

    val authorLabel = LinkLabel.create("") {
      BrowserUtil.browse(author.url)
    }.apply {
      icon = avatarIconsProvider.getIcon(author.avatarUrl)
      isFocusable = true
      border = JBUI.Borders.empty(getEditorTextFieldVerticalOffset() - 2, 0)
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

    return create(textField, submitButton, cancelButton, authorLabel, onCancel)
  }

  private fun create(textField: EditorTextField,
                     submitButton: InlineIconButton,
                     cancelButton: InlineIconButton,
                     authorLabel: LinkLabel<out Any>? = null,
                     onCancel: (() -> Unit)? = null): JComponent {

    val busyLabel = JLabel(AnimatedIcon.Default())
    val textFieldWithOverlay = createTextFieldWithOverlay(textField, submitButton, busyLabel)
    val panel = JPanel(null).apply {
      isOpaque = false
      layout = MigLayout(LC().gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .fillX())

      if (authorLabel != null) {
        isFocusCycleRoot = true
        isFocusTraversalPolicyProvider = true
        focusTraversalPolicy = ListFocusTraversalPolicy(listOf(textField, authorLabel))
        add(authorLabel, CC().alignY("top").gapRight("${UI.scale(6)}"))
      }

      add(textFieldWithOverlay, CC().grow().pushX())
      add(cancelButton, CC().alignY("top").hideMode(3))
    }
    Controller(panel, textField, busyLabel, submitButton, cancelButton, onCancel)
    UiNotifyConnector(textField, ValidatorActivatable(textField))

    return panel
  }

  private inner class ValidatorActivatable(private val textField: EditorTextField) : Activatable {

    private var validatorDisposable: Disposable? = null
    private var validator: ComponentValidator? = null

    init {
      model.addStateListener {
        validator?.revalidate()
      }
    }

    override fun showNotify() {
      validatorDisposable = Disposer.newDisposable("ETF validator")
      validator = ComponentValidator(validatorDisposable!!).withValidator(Supplier {
        model.error?.let { ValidationInfo(it.message.orEmpty(), textField) }
      }).installOn(textField)
    }

    override fun hideNotify() {
      validatorDisposable?.let { Disposer.dispose(it) }
      validatorDisposable = null
      validator = null
    }
  }

  private fun createTextField(placeHolder: String): EditorTextField {

    return object : EditorTextField(model.document, null, FileTypes.PLAIN_TEXT) {
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
      setPlaceholder(placeHolder)
      addSettingsProvider {
        it.putUserData(IncrementalFindAction.SEARCH_DISABLED, true)
        it.colorsScheme.lineSpacing = 1f
        it.settings.isUseSoftWraps = true
      }
      selectAll()
    }
  }

  private fun createSubmitButton(actionName: String) =
    InlineIconButton(GithubIcons.Send, GithubIcons.SendHovered, tooltip = actionName, shortcut = SUBMIT_SHORTCUT_SET).apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

  private fun createCancelButton() =
    InlineIconButton(AllIcons.Actions.Close, AllIcons.Actions.CloseHovered, tooltip = Messages.getCancelButton(),
                     shortcut = CANCEL_SHORTCUT_SET).apply {
      border = JBUI.Borders.empty(getEditorTextFieldVerticalOffset(), 0)
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
    }

  private fun getEditorTextFieldVerticalOffset() = if (UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()) 6 else 4

  private fun createTextFieldWithOverlay(textField: EditorTextField, button: JComponent, busyLabel: JComponent): JComponent {

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
        busyLabel.bounds = SingleComponentCenteringLayout.getBoundsForCentered(textField, busyLabel)
      }
    }
    layeredPane.add(textField, JLayeredPane.DEFAULT_LAYER, 0)
    layeredPane.add(busyLabel, JLayeredPane.POPUP_LAYER, 1)
    layeredPane.add(button, JLayeredPane.POPUP_LAYER, 2)

    return layeredPane
  }

  private inner class Controller(private val panel: JPanel,
                                 private val textField: EditorTextField,
                                 private val busyLabel: JLabel,
                                 private val submitButton: InlineIconButton,
                                 cancelButton: InlineIconButton,
                                 onCancel: (() -> Unit)?) {
    init {
      textField.addDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
          update()
          panel.revalidate()
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

      model.addStateListener(::update)
      update()
    }

    private fun update() {
      busyLabel.isVisible = model.isBusy
      submitButton.isEnabled = !model.isBusy && textField.text.isNotBlank()
    }
  }
}