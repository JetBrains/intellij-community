// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("FunctionName")
package com.intellij.ui.components

import com.intellij.BundleBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.asBrowseFolderDescriptor
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.ui.ComponentWithBrowseButton.BrowseFolderActionListener
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextComponentAccessors
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.openapi.util.NlsContexts.BorderTitle
import com.intellij.openapi.util.NlsContexts.Checkbox
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.openapi.util.NlsContexts.Label
import com.intellij.openapi.util.NlsContexts.RadioButton
import com.intellij.openapi.util.NlsContexts.Tooltip
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton
import com.intellij.util.FontUtil
import com.intellij.util.SmartList
import com.intellij.util.ui.SwingHelper.addHistoryOnExpansion
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.LayoutManager2
import javax.swing.Action
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JRadioButton
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.text.BadLocationException
import javax.swing.text.JTextComponent
import javax.swing.text.Segment

@ApiStatus.ScheduledForRemoval
@Deprecated("Use correspondent constructors JLabel/JBLabel/MultiLineLabel, depends on situation", level = DeprecationLevel.HIDDEN)
fun Label(@Label text: String, style: UIUtil.ComponentStyle? = null, fontColor: UIUtil.FontColor? = null, bold: Boolean = false): JLabel {
  return createLabelInt(text = text, style = style, fontColor = fontColor, bold = bold, font = null)
}

/**
 * Always calls [BundleBase.replaceMnemonicAmpersand] inside and therefore can log the text in case of several mnemonics.
 * That's unexpected behavior
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use correspondent constructors JLabel/JBLabel/MultiLineLabel, depends on situation")
private fun createLabelInt(
  @Label text: String,
  style: UIUtil.ComponentStyle? = null,
  fontColor: UIUtil.FontColor? = null,
  bold: Boolean = false,
  font: Font? = null,
): JLabel {
  val finalText = BundleBase.replaceMnemonicAmpersand(text)!!
  val label: JLabel
  if (fontColor == null) {
    label = if (finalText.contains('\n')) MultiLineLabel(finalText) else JLabel(finalText)
  }
  else {
    label = JBLabel(finalText, UIUtil.ComponentStyle.REGULAR, fontColor)
  }

  if (font != null) {
    label.font = font
  } else {
    style?.let { UIUtil.applyStyle(it, label) }
    if (bold) {
      label.font = label.font.deriveFont(Font.BOLD)
    }
  }

  // surrounded by space to avoid false match
  if (text.contains(" -> ")) {
    @Suppress("HardCodedStringLiteral")
    label.text = text.replace(" -> ", " ${FontUtil.rightArrow(label.font)} ")
  }
  return label
}

@ApiStatus.Internal
@Deprecated("Use Kotlin UI DSL")
fun RadioButton(@RadioButton text: String): JRadioButton = JRadioButton(BundleBase.replaceMnemonicAmpersand(text))

@ApiStatus.Internal
@Deprecated("Use Kotlin UI DSL")
fun CheckBox(@Checkbox text: String, selected: Boolean = false, toolTip: @Tooltip String? = null): JCheckBox {
  val component = JCheckBox(BundleBase.replaceMnemonicAmpersand(text), selected)
  toolTip?.let { component.toolTipText = it }
  return component
}

fun DialogPanel(title: @BorderTitle String? = null, layout: LayoutManager2? = BorderLayout()): DialogPanel {
  val panel = DialogPanel(layout)
  title?.let { setTitledBorder(it, panel) }
  return panel
}

private fun setTitledBorder(@BorderTitle title: String, panel: JPanel) {
  val border = IdeBorderFactory.createTitledBorder(title, false)
  panel.border = border
  border.acceptMinimumSize(panel)
}

/**
 * Consider using [UI DSL](https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl-version-2.html).
 */
@JvmOverloads
@ApiStatus.Internal
fun dialog(@DialogTitle title: String,
           panel: JComponent,
           resizable: Boolean = false,
           focusedComponent: JComponent? = null,
           okActionEnabled: Boolean = true,
           project: Project? = null,
           parent: Component? = null,
           @DialogMessage errorText: String? = null,
           modality: IdeModalityType = IdeModalityType.IDE,
           createActions: ((DialogManager) -> List<Action>)? = null,
           ok: (() -> List<ValidationInfo>?)? = null): DialogWrapper {
  return object : MyDialogWrapper(project, parent, modality) {
    init {
      setTitle(title)
      isResizable = resizable

      if (!okActionEnabled) {
        this.okAction.isEnabled = false
      }

      setErrorText(errorText)

      init()
    }

    override fun createCenterPanel() = panel

    override fun createActions(): Array<out Action> {
      return if (createActions == null) super.createActions() else createActions(this).toTypedArray()
    }

    override fun getPreferredFocusedComponent() = focusedComponent ?: super.getPreferredFocusedComponent()

    override fun doOKAction() {
      if (okAction.isEnabled) {
        performAction(ok)
      }
    }
  }
}

interface DialogManager {
  fun performAction(action: (() -> List<ValidationInfo>?)? = null)
}

private abstract class MyDialogWrapper(
  project: Project?,
  parent: Component?,
  modality: IdeModalityType,
) : DialogWrapper(project, parent, true, modality), DialogManager {
  override fun performAction(action: (() -> List<ValidationInfo>?)?) {
    val validationInfoList = action?.invoke()
    if (validationInfoList.isNullOrEmpty()) {
      super.doOKAction()
    }
    else {
      setErrorInfoAll(validationInfoList)
      clearErrorInfoOnFirstChange(validationInfoList)
    }
  }

  private fun getTextField(info: ValidationInfo): JTextComponent? {
    val component = info.component ?: return null
    return when (component) {
      is JTextComponent -> component
      is TextFieldWithBrowseButton -> component.textField
      else -> null
    }
  }

  private fun clearErrorInfoOnFirstChange(validationInfoList: List<ValidationInfo>) {
    val unchangedFields = SmartList<Component>()
    for (info in validationInfoList) {
      val textField = getTextField(info) ?: continue
      unchangedFields.add(textField)
      textField.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          textField.document.removeDocumentListener(this)
          if (unchangedFields.remove(textField) && unchangedFields.isEmpty()) {
            setErrorInfoAll(emptyList())
          }
        }
      })
    }
  }
}

@JvmOverloads
@ApiStatus.Internal
fun <T : JComponent> installFileCompletionAndBrowseDialog(
  project: Project?,
  component: ComponentWithBrowseButton<T>,
  textField: JTextField,
  fileChooserDescriptor: FileChooserDescriptor,
  textComponentAccessor: TextComponentAccessor<T>,
  fileChosen: ((chosenFile: VirtualFile) -> String)? = null
) {
  if (ApplicationManager.getApplication() == null) {
    return // tests
  }
  val browseFolderDescriptor = fileChooserDescriptor.asBrowseFolderDescriptor()
  if (fileChosen != null) {
    browseFolderDescriptor.convertFileToText = fileChosen
  }
  component.addActionListener(BrowseFolderActionListener(component, project, browseFolderDescriptor, textComponentAccessor))
  FileChooserFactory.getInstance().installFileCompletion(textField, fileChooserDescriptor, true, null /*infer disposable from context*/)
}

@JvmOverloads
fun textFieldWithHistoryWithBrowseButton(
  project: Project?,
  fileChooserDescriptor: FileChooserDescriptor,
  historyProvider: (() -> List<String>)? = null,
  fileChosen: ((chosenFile: VirtualFile) -> String)? = null
): TextFieldWithHistoryWithBrowseButton {
  val component = TextFieldWithHistoryWithBrowseButton()
  val textFieldWithHistory = component.childComponent
  textFieldWithHistory.setHistorySize(-1)
  textFieldWithHistory.setMinimumAndPreferredWidth(0)
  if (historyProvider != null) {
    addHistoryOnExpansion(textFieldWithHistory, historyProvider)
  }
  val textField = component.childComponent.textEditor
  val textComponentAccessor = TextComponentAccessors.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT
  installFileCompletionAndBrowseDialog(project, component, textField, fileChooserDescriptor, textComponentAccessor, fileChosen)
  return component
}

@JvmOverloads
@ApiStatus.Internal
fun textFieldWithBrowseButton(
  project: Project?,
  fileChooserDescriptor: FileChooserDescriptor,
  fileChosen: ((chosenFile: VirtualFile) -> String)? = null
): TextFieldWithBrowseButton {
  val component = TextFieldWithBrowseButton()
  val textComponentAccessor = TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
  installFileCompletionAndBrowseDialog(project, component, component.textField, fileChooserDescriptor, textComponentAccessor, fileChosen)
  return component
}

@JvmOverloads
@ApiStatus.Internal
fun textFieldWithBrowseButton(
  project: Project?,
  textField: JTextField,
  fileChooserDescriptor: FileChooserDescriptor,
  fileChosen: ((chosenFile: VirtualFile) -> String)? = null
): TextFieldWithBrowseButton {
  val component = TextFieldWithBrowseButton(textField)
  val textComponentAccessor = TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
  installFileCompletionAndBrowseDialog(project, component, component.textField, fileChooserDescriptor, textComponentAccessor, fileChosen)
  return component
}

@get:ApiStatus.Internal
val JPasswordField.chars: CharSequence?
  get() {
    val doc = document
    if (doc.length == 0) {
      return ""
    }
    else try {
      return Segment().also { doc.getText(0, doc.length, it) }
    }
    catch (_: BadLocationException) {
      return null
    }
  }
