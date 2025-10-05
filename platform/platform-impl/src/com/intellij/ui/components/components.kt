// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("FunctionName")
package com.intellij.ui.components

import com.intellij.BundleBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.*
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.asBrowseFolderDescriptor
import com.intellij.openapi.ui.ComponentWithBrowseButton.BrowseFolderActionListener
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.openapi.util.NlsContexts.*
import com.intellij.openapi.util.NlsContexts.Checkbox
import com.intellij.openapi.util.NlsContexts.Label
import com.intellij.openapi.vcs.changes.issueLinks.LinkMouseListenerBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.util.Consumer
import com.intellij.util.FontUtil
import com.intellij.util.SmartList
import com.intellij.util.io.URLUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.SwingHelper.addHistoryOnExpansion
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.HyperlinkListener
import javax.swing.text.BadLocationException
import javax.swing.text.JTextComponent
import javax.swing.text.Segment

@ApiStatus.ScheduledForRemoval
@Deprecated("Use correspondent constructors JLabel/JBLabel/MultiLineLabel, depends on situation")
fun Label(@Label text: String, style: UIUtil.ComponentStyle? = null, fontColor: UIUtil.FontColor? = null, bold: Boolean = false): JLabel {
  return Label(text = text, style = style, fontColor = fontColor, bold = bold, font = null)
}

/**
 * Always calls [BundleBase.replaceMnemonicAmpersand] inside and therefore can log the text in case of several mnemonics.
 * That's unexpected behavior
 */
@ApiStatus.ScheduledForRemoval
@ApiStatus.Internal
@Deprecated("Use correspondent constructors JLabel/JBLabel/MultiLineLabel, depends on situation")
fun Label(
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

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL, method Row.link", level = DeprecationLevel.ERROR)
fun Link(@Label text: String, style: UIUtil.ComponentStyle? = null, action: () -> Unit): JComponent {
  val result = ActionLink(text) { action() }
  style?.let { UIUtil.applyStyle(it, result) }
  return result
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL, methods like Row.text, Row.comment or Cell.comment")
@JvmOverloads
fun noteComponent(@Label note: String, linkHandler: ((url: String) -> Unit)? = null): JComponent {
  val matcher = URLUtil.HREF_PATTERN.matcher(note)
  if (!matcher.find()) {
    return Label(note)
  }

  val noteComponent = SimpleColoredComponent()
  var prev = 0
  do {
    if (matcher.start() != prev) {
      @Suppress("HardCodedStringLiteral")
      noteComponent.append(note.substring(prev, matcher.start()))
    }

    val linkUrl = matcher.group(1)
    val tag = if (linkHandler == null) SimpleColoredComponent.BrowserLauncherTag(linkUrl) else Runnable { linkHandler(linkUrl) }
    noteComponent.append(matcher.group(2), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, tag)
    prev = matcher.end()
  }
  while (matcher.find())

  LinkMouseListenerBase.installSingleTagOn(noteComponent)

  if (prev < note.length) {
    @Suppress("HardCodedStringLiteral")
    noteComponent.append(note.substring(prev))
  }

  return noteComponent
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use Kotlin UI DSL, method Row.text")
@JvmOverloads
fun htmlComponent(@DetailedDescription text: String = "",
                  font: Font? = null,
                  background: Color? = null,
                  foreground: Color? = null,
                  lineWrap: Boolean = false,
                  hyperlinkListener: HyperlinkListener? = BrowserHyperlinkListener.INSTANCE): JEditorPane {
  val pane = SwingHelper.createHtmlViewer(lineWrap, font, background, foreground)
  pane.text = text
  pane.border = null
  pane.disabledTextColor = UIUtil.getLabelDisabledForeground()
  if (hyperlinkListener != null) {
    pane.addHyperlinkListener(hyperlinkListener)
  }
  return pane
}

fun RadioButton(@RadioButton text: String): JRadioButton = JRadioButton(BundleBase.replaceMnemonicAmpersand(text))

fun CheckBox(@Checkbox text: String, selected: Boolean = false, toolTip: @Tooltip String? = null): JCheckBox {
  val component = JCheckBox(BundleBase.replaceMnemonicAmpersand(text), selected)
  toolTip?.let { component.toolTipText = it }
  return component
}

@ApiStatus.ScheduledForRemoval
@ApiStatus.Internal
@Deprecated("Use Kotlin UI DSL, method Panel.group")
@JvmOverloads
fun Panel(@BorderTitle title: String? = null, layout: LayoutManager2? = BorderLayout()): JPanel {
  val panel = JPanel(layout)
  title?.let {
    @Suppress("HardCodedStringLiteral")
    setTitledBorder(title = it, panel = panel, hasSeparator = false)
  }
  return panel
}

fun DialogPanel(title: @BorderTitle String? = null, layout: LayoutManager2? = BorderLayout()): DialogPanel {
  val panel = DialogPanel(layout)
  title?.let { setTitledBorder(it, panel, hasSeparator = true) }
  return panel
}

private fun setTitledBorder(@BorderTitle title: String, panel: JPanel, hasSeparator: Boolean) {
  val border = when {
    hasSeparator -> IdeBorderFactory.createTitledBorder(title, false)
    else -> IdeBorderFactory.createTitledBorder(title, false, JBUI.insetsTop(8)).setShowLine(false)
  }
  panel.border = border
  border.acceptMinimumSize(panel)
}

/**
 * Consider using [UI DSL](https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl-version-2.html).
 */
@JvmOverloads
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

@Deprecated(
  "Use `textFieldWithHistoryWithBrowseButton(Project, FileChooserDescriptor, () -> List<String>, (VirtualFile) -> String)` together with `FileChooserDescriptor#withTitle`",
  level = DeprecationLevel.ERROR
)
@JvmOverloads
fun textFieldWithHistoryWithBrowseButton(
  project: Project?,
  @DialogTitle browseDialogTitle: String,
  fileChooserDescriptor: FileChooserDescriptor,
  historyProvider: (() -> List<String>)? = null,
  fileChosen: ((chosenFile: VirtualFile) -> String)? = null
): TextFieldWithHistoryWithBrowseButton = textFieldWithHistoryWithBrowseButton(project, fileChooserDescriptor.withTitle(browseDialogTitle), historyProvider, fileChosen)

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

@Deprecated(
  "Use `textFieldWithBrowseButton(Project, FileChooserDescriptor, (VirtualFile) -> String)` together with `FileChooserDescriptor#withTitle`",
  level = DeprecationLevel.ERROR
)
@JvmOverloads
fun textFieldWithBrowseButton(
  project: Project?,
  @DialogTitle browseDialogTitle: String?,
  fileChooserDescriptor: FileChooserDescriptor,
  fileChosen: ((chosenFile: VirtualFile) -> String)? = null
): TextFieldWithBrowseButton = textFieldWithBrowseButton(project, fileChooserDescriptor.withTitle(browseDialogTitle), fileChosen)

@JvmOverloads
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
