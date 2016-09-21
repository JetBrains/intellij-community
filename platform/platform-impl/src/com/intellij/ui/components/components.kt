/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.components

import com.intellij.BundleBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.openapi.vcs.changes.issueLinks.LinkMouseListenerBase
import com.intellij.ui.*
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.util.regex.Pattern
import javax.swing.*

private val HREF_PATTERN = Pattern.compile("<a(?:\\s+href\\s*=\\s*[\"']([^\"']*)[\"'])?\\s*>([^<]*)</a>")

private val LINK_TEXT_ATTRIBUTES: SimpleTextAttributes
  get() = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UI.getColor("link.foreground"))

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

  return label
}

fun Link(text: String, style: UIUtil.ComponentStyle? = null, action: () -> Unit): JComponent {
  val result = LinkLabel.create(text, action)
  style?.let { UIUtil.applyStyle(it, result) }
  return result
}

fun noteComponent(note: String): JComponent {
  val matcher = HREF_PATTERN.matcher(note)
  if (!matcher.find()) {
    return Label(note)
  }

  val noteComponent = SimpleColoredComponent()
  var prev = 0
  do {
    if (matcher.start() != prev) {
      noteComponent.append(note.substring(prev, matcher.start()))
    }
    noteComponent.append(matcher.group(2), LINK_TEXT_ATTRIBUTES, SimpleColoredComponent.BrowserLauncherTag(matcher.group(1)))
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
fun htmlComponent(text: String = "", font: Font = UIUtil.getLabelFont(), background: Color? = null, foreground: Color? = null, lineWrap: Boolean = false): JEditorPane {
  val pane = SwingHelper.createHtmlViewer(lineWrap, font, background, foreground)
  if (!text.isNullOrEmpty()) {
    pane.text = "<html><head>${UIUtil.getCssFontDeclaration(font, UIUtil.getLabelForeground(), null, null)}</head><body>$text</body></html>"
  }
  pane.border = null
  pane.disabledTextColor = UIUtil.getLabelDisabledForeground()
  pane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
  return pane
}

fun RadioButton(text: String) = JRadioButton(BundleBase.replaceMnemonicAmpersand(text))

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

private fun setTitledBorder(title: String, panel: JPanel) {
  val border = IdeBorderFactory.createTitledBorder(title, false)
  panel.border = border
  border.acceptMinimumSize(panel)
}

fun dialog(title: String,
           panel: JComponent,
           resizable: Boolean = false,
           focusedComponent: JComponent? = null,
           okActionEnabled: Boolean = true,
           project: Project? = null,
           parent: Component? = null,
           errorText: String? = null,
           modality: IdeModalityType = IdeModalityType.IDE,
           ok: (() -> Unit)? = null): DialogWrapper {
  return object: DialogWrapper(project, parent, true, modality) {
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

    override fun getPreferredFocusedComponent() = focusedComponent

    override fun doOKAction() {
      ok?.let {
        if (okAction.isEnabled) {
          it()
        }
      }
      super.doOKAction()
    }
  }
}