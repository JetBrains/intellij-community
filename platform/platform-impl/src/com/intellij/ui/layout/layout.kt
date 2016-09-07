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
package com.intellij.ui.layout

import com.intellij.BundleBase
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.noteComponent
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.ComponentStyle
import net.miginfocom.layout.BoundSize
import net.miginfocom.layout.CC
import net.miginfocom.layout.UnitValue
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.event.ActionEvent
import javax.swing.*

inline fun panel(vararg layoutConstraints: LCFlags, title: String? = null, init: Panel.() -> Unit) = createPanel(title, layoutConstraints, init)

inline fun Panel.panel(title: String, layoutConstraints: Array<out LCFlags> = emptyArray(), vararg constraints: CCFlags, init: Panel.() -> Unit) {
  add(createPanel(title, layoutConstraints, init), constraints.create())
}

fun titledPanel(title: String): JPanel {
  val panel = JPanel(BorderLayout())
  setTitledBorder(title, panel)
  return panel
}

fun JPanel.titledPanel(title: String, wrappedComponent: Component, vararg constraints: CCFlags) {
  val panel = titledPanel(title)
  panel.add(wrappedComponent)
  add(panel, constraints.create())
}

fun JPanel.label(text: String,
                 vararg constraints: CCFlags,
                 style: ComponentStyle? = null,
                 fontColor: UIUtil.FontColor? = null,
                 bold: Boolean = false,
                 gapLeft: Int = 0,
                 gapBottom: Int = 0,
                 gapAfter: Int = 0,
                 split: Int = -1) {
  val finalText = BundleBase.replaceMnemonicAmpersand(text)
  val label: JLabel
  if (fontColor == null) {
    label = if (finalText.contains('\n')) MultiLineLabel(finalText) else JLabel(finalText)
    style?.let { UIUtil.applyStyle(it, label) }
  }
  else {
    label = JBLabel(finalText, style ?: ComponentStyle.REGULAR, fontColor)
  }

  if (bold) {
    label.font = label.font.deriveFont(Font.BOLD)
  }

  add(label, createComponentConstraints(constraints, gapLeft = gapLeft, gapBottom = gapBottom, gapAfter = gapAfter, split = split))
}

private fun createComponentConstraints(constraints: Array<out CCFlags>,
                                       gapLeft: Int = 0,
                                       gapAfter: Int = 0,
                                       gapTop: Int = 0,
                                       gapBottom: Int = 0,
                                       split: Int = -1): CC? {
  var _cc = constraints.create()
  fun cc(): CC {
    if (_cc == null) {
      _cc = CC()
    }
    return _cc!!
  }

  if (gapLeft != 0) {
    cc().horizontal.gapBefore = gapToBoundSize(gapLeft, true)
  }
  if (gapAfter != 0) {
    cc().horizontal.gapAfter = gapToBoundSize(gapAfter, true)
  }

  if (gapTop != 0) {
    cc().vertical.gapBefore = gapToBoundSize(gapTop, false)
  }
  if (gapBottom != 0) {
    cc().vertical.gapAfter = gapToBoundSize(gapBottom, false)
  }

  if (split != -1) {
    cc().split = split
  }
  return _cc
}

fun JPanel.link(text: String, vararg constraints: CCFlags, style: ComponentStyle? = null, action: () -> Unit) {
  val result = LinkLabel.create(text, action)
  style?.let { UIUtil.applyStyle(it, result) }
  add(result, constraints.create())
}

fun JPanel.link(text: String, url: String, vararg constraints: CCFlags, style: ComponentStyle? = null) {
  val result = LinkLabel.create(text, { BrowserUtil.browse(url) })
  style?.let { UIUtil.applyStyle(it, result) }
  add(result, constraints.create())
}

private fun gapToBoundSize(value: Int, isHorizontal: Boolean): BoundSize {
  val unitValue = UnitValue(value.toFloat(), "", isHorizontal, UnitValue.STATIC, null)
  return BoundSize(unitValue, unitValue, null, false, null)
}

fun JPanel.hint(text: String, vararg constraints: CCFlags) {
  label(text, style = ComponentStyle.SMALL, fontColor = UIUtil.FontColor.BRIGHTER, constraints = *constraints, gapLeft = 3 * GAP)
}

/**
 * Hyperlinks are supported (`<a href=""></a>`), new lines and <br> are not supported.
 */
fun JPanel.note(text: String, vararg constraints: CCFlags) {
  add(noteComponent(text), createComponentConstraints(constraints, gapTop = GAP))
}

fun RadioButton(text: String) = JRadioButton(BundleBase.replaceMnemonicAmpersand(text))

fun JPanel.radioButton(text: String, vararg constraints: CCFlags) {
  add(RadioButton(BundleBase.replaceMnemonicAmpersand(text)), constraints.create())
}

fun JPanel.buttonGroup(vararg buttons: AbstractButton) {
  val group = ButtonGroup()
  buttons.forEach {
    group.add(it)
    add(it)
  }
}

fun JPanel.button(text: String, vararg constraints: CCFlags, actionListener: (event: ActionEvent) -> Unit) {
  val button = JButton(BundleBase.replaceMnemonicAmpersand(text))
  button.addActionListener(actionListener)
  add(button, constraints.create())
}

fun JPanel.add(component: Component, vararg constraints: CCFlags) {
  add(component, constraints.create())
}