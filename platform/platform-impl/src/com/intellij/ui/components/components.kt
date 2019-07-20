// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("FunctionName")

package com.intellij.ui.components

import com.intellij.BundleBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.*
import com.intellij.openapi.ui.ComponentWithBrowseButton.BrowseFolderActionListener
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.openapi.vcs.changes.issueLinks.LinkMouseListenerBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.FontUtil
import com.intellij.util.SmartList
import com.intellij.util.io.URLUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.SwingHelper.addHistoryOnExpansion
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.HyperlinkListener
import javax.swing.text.BadLocationException
import javax.swing.text.JTextComponent
import javax.swing.text.Segment

private val LINK_TEXT_ATTRIBUTES: SimpleTextAttributes
  get() = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBUI.CurrentTheme.Link.linkColor())

fun Label(text: String, style: UIUtil.ComponentStyle? = null, fontColor: UIUtil.FontColor? = null, bold: Boolean = false): JLabel {
  val finalText = BundleBase.replaceMnemonicAmpersand(text)
  val label: JLabel
  if (fontColor == null) {
    label = if (finalText.contains('\n')) MultiLineLabel(finalText) else JLabel(finalText)
    style?.let { UIUtil.applyStyle(it, label) }
  }
  else {
    label = JBLabel(finalText, style ?: UIUtil.ComponentStyle.REGULAR, fontColor)
  }

  if (bold) {
    label.font = label.font.deriveFont(Font.BOLD)
  }

  // surrounded by space to avoid false match
  if (text.contains(" -> ")) {
    label.text = text.replace(" -> ", " ${FontUtil.rightArrow(label.font)} ")
  }
  return label
}

fun Link(text: String, style: UIUtil.ComponentStyle? = null, action: () -> Unit): JComponent {
  val result = LinkLabel.create(text, action)
  style?.let { UIUtil.applyStyle(it, result) }
  return result
}

@JvmOverloads
fun noteComponent(note: String, linkHandler: ((url: String) -> Unit)? = null): JComponent {
  val matcher = URLUtil.HREF_PATTERN.matcher(note)
  if (!matcher.find()) {
    return Label(note)
  }

  val noteComponent = SimpleColoredComponent()
  var prev = 0
  do {
    if (matcher.start() != prev) {
      noteComponent.append(note.substring(prev, matcher.start()))
    }

    val linkUrl = matcher.group(1)
    val tag = if (linkHandler == null) SimpleColoredComponent.BrowserLauncherTag(linkUrl) else Runnable { linkHandler(linkUrl) }
    noteComponent.append(matcher.group(2), LINK_TEXT_ATTRIBUTES, tag)
    prev = matcher.end()
  }
  while (matcher.find())

  LinkMouseListenerBase.installSingleTagOn(noteComponent)

  if (prev < note.length) {
    noteComponent.append(note.substring(prev))
  }

  return noteComponent
}

@JvmOverloads
fun htmlComponent(text: String = "",
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

fun RadioButton(text: String): JRadioButton = JRadioButton(BundleBase.replaceMnemonicAmpersand(text))

fun CheckBox(text: String, selected: Boolean = false, toolTip: String? = null): JCheckBox {
  val component = JCheckBox(BundleBase.replaceMnemonicAmpersand(text), selected)
  toolTip?.let { component.toolTipText = it }
  return component
}

@JvmOverloads
fun Panel(title: String? = null, layout: LayoutManager2? = BorderLayout()): JPanel {
  val panel = JPanel(layout)
  title?.let { setTitledBorder(it, panel) }
  return panel
}

fun DialogPanel(title: String? = null, layout: LayoutManager2? = BorderLayout()): DialogPanel {
  val panel = DialogPanel(layout)
  title?.let { setTitledBorder(it, panel) }
  return panel
}

private fun setTitledBorder(title: String, panel: JPanel) {
  val border = IdeBorderFactory.createTitledBorder(title, false)
  panel.border = border
  border.acceptMinimumSize(panel)
}

/**
 * Consider using [UI DSL](https://github.com/JetBrains/intellij-community/tree/master/platform/platform-impl/src/com/intellij/ui/layout#readme).
 */
@JvmOverloads
fun dialog(title: String,
           panel: JComponent,
           resizable: Boolean = false,
           focusedComponent: JComponent? = null,
           okActionEnabled: Boolean = true,
           project: Project? = null,
           parent: Component? = null,
           errorText: String? = null,
           modality: IdeModalityType = IdeModalityType.IDE,
           createActions: ((DialogManager) -> List<Action>)? = null,
           ok: (() -> List<ValidationInfo>?)? = null): DialogWrapper {
  return object : MyDialogWrapper(project, parent, modality) {
    init {
      setTitle(title)
      setResizable(resizable)

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

private abstract class MyDialogWrapper(project: Project?,
                                       parent: Component?,
                                       modality: IdeModalityType) : DialogWrapper(project, parent, true, modality), DialogManager {
  override fun performAction(action: (() -> List<ValidationInfo>?)?) {
    val validationInfoList = action?.invoke()
    if (validationInfoList == null || validationInfoList.isEmpty()) {
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
fun <T : JComponent> installFileCompletionAndBrowseDialog(project: Project?,
                                                          component: ComponentWithBrowseButton<T>,
                                                          textField: JTextField,
                                                          @Nls(capitalization = Nls.Capitalization.Title) browseDialogTitle: String,
                                                          fileChooserDescriptor: FileChooserDescriptor,
                                                          textComponentAccessor: TextComponentAccessor<T>,
                                                          fileChosen: ((chosenFile: VirtualFile) -> String)? = null) {
  if (ApplicationManager.getApplication() == null) {
    // tests
    return
  }

  component.addActionListener(
    object : BrowseFolderActionListener<T>(browseDialogTitle, null, component, project, fileChooserDescriptor, textComponentAccessor) {
      override fun onFileChosen(chosenFile: VirtualFile) {
        if (fileChosen == null) {
          super.onFileChosen(chosenFile)
        }
        else {
          textComponentAccessor.setText(myTextComponent, fileChosen(chosenFile))
        }
      }
    })
  FileChooserFactory.getInstance().installFileCompletion(textField, fileChooserDescriptor, true, project)
}

@JvmOverloads
fun textFieldWithHistoryWithBrowseButton(project: Project?,
                                         browseDialogTitle: String,
                                         fileChooserDescriptor: FileChooserDescriptor,
                                         historyProvider: (() -> List<String>)? = null,
                                         fileChosen: ((chosenFile: VirtualFile) -> String)? = null): TextFieldWithHistoryWithBrowseButton {
  val component = TextFieldWithHistoryWithBrowseButton()
  val textFieldWithHistory = component.childComponent
  textFieldWithHistory.setHistorySize(-1)
  textFieldWithHistory.setMinimumAndPreferredWidth(0)
  if (historyProvider != null) {
    addHistoryOnExpansion(textFieldWithHistory, historyProvider)
  }
  installFileCompletionAndBrowseDialog(
    project = project,
    component = component,
    textField = component.childComponent.textEditor,
    browseDialogTitle = browseDialogTitle,
    fileChooserDescriptor = fileChooserDescriptor,
    textComponentAccessor = TextComponentAccessor.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT,
    fileChosen = fileChosen
  )
  return component
}

@JvmOverloads
fun textFieldWithBrowseButton(project: Project?,
                              browseDialogTitle: String,
                              fileChooserDescriptor: FileChooserDescriptor,
                              fileChosen: ((chosenFile: VirtualFile) -> String)? = null): TextFieldWithBrowseButton {
  val component = TextFieldWithBrowseButton()
  installFileCompletionAndBrowseDialog(
    project = project,
    component = component,
    textField = component.textField,
    browseDialogTitle = browseDialogTitle,
    fileChooserDescriptor = fileChooserDescriptor,
    textComponentAccessor = TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
    fileChosen = fileChosen
  )
  return component
}

@JvmOverloads
fun textFieldWithBrowseButton(project: Project?,
                              browseDialogTitle: String,
                              textField: JTextField,
                              fileChooserDescriptor: FileChooserDescriptor,
                              fileChosen: ((chosenFile: VirtualFile) -> String)? = null): TextFieldWithBrowseButton {
  val component = TextFieldWithBrowseButton(textField)
  installFileCompletionAndBrowseDialog(
    project = project,
    component = component,
    textField = component.textField,
    browseDialogTitle = browseDialogTitle,
    fileChooserDescriptor = fileChooserDescriptor,
    textComponentAccessor = TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
    fileChosen = fileChosen
  )
  return component
}


val JPasswordField.chars: CharSequence?
  get() {
    val doc = document
    if (doc.length == 0) {
      return ""
    }

    val segment = Segment()
    try {
      doc.getText(0, doc.length, segment)
    }
    catch (e: BadLocationException) {
      return null
    }
    return segment
  }