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
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.*
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.LayoutManager
import java.awt.event.ActionEvent
import javax.swing.*

inline fun panel(vararg layoutConstraints: LCFlags, init: Panel.() -> Unit): JPanel {
  val panel = Panel(MigLayout(c().apply(layoutConstraints)))
  panel.init()
  return panel
}

fun titledPanel(title: String): JPanel {
  val panel = JPanel(BorderLayout())
  val border = IdeBorderFactory.createTitledBorder(title, false)
  panel.border = border
  border.acceptMinimumSize(panel)
  return panel
}

fun JPanel.titledPanel(title: String, wrappedComponent: Component, vararg constraints: CCFlags) {
  val panel = titledPanel(title)
  panel.add(wrappedComponent)
  add(panel, *constraints)
}

fun JPanel.label(text: String,
                 vararg constraints: CCFlags,
                 componentStyle: UIUtil.ComponentStyle? = null,
                 fontColor: UIUtil.FontColor? = null,
                 bold: Boolean = false,
                 gapLeft: Int = 0,
                 gapBottom: Int = 0,
                 gapAfter: Int = 0,
                 split: Int = -1) {
  val finalText = BundleBase.replaceMnemonicAmpersand(text)
  val label = if (componentStyle == null && fontColor == null) {
    JLabel(finalText)
  }
  else {
    JBLabel(finalText, componentStyle ?: UIUtil.ComponentStyle.REGULAR, fontColor ?: UIUtil.FontColor.NORMAL)
  }

  if (bold) {
    label.font = label.font.deriveFont(Font.BOLD)
  }

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
  if (gapBottom != 0) {
    cc().vertical.gapAfter = gapToBoundSize(gapBottom, false)
  }
  if (split != -1) {
    cc().split = split
  }
  add(label, _cc)
}

fun JPanel.link(text: String, vararg constraints: CCFlags, action: () -> Unit) {
  add(LinkLabel.create(text, action), constraints.create())
}

fun JPanel.link(text: String, url: String, vararg constraints: CCFlags) {
  add(LinkLabel.create(text, { BrowserUtil.browse(url) }), constraints.create())
}

private fun gapToBoundSize(value: Int, isHorizontal: Boolean): BoundSize {
  val unitValue = UnitValue(value.toFloat(), "", isHorizontal, UnitValue.STATIC, null)
  return BoundSize(unitValue, unitValue, null, false, null)
}

fun JPanel.hint(text: String, vararg constraints: CCFlags) {
  // default gap 10 * indent 3
  label(text, componentStyle = UIUtil.ComponentStyle.SMALL, fontColor = UIUtil.FontColor.BRIGHTER, constraints = *constraints, gapLeft = 30)
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

// default values differs to MigLayout - IntelliJ Platform defaults are used
// see com.intellij.uiDesigner.core.AbstractLayout.DEFAULT_HGAP and DEFAULT_VGAP (multiplied by 2 to achieve the same look (it seems in terms of MigLayout gap is both left and right space))
fun c(insets: String? = "0", gap: String? = "20 5"): LC {
  // no setter for gap, so, create string to parse
  val lc = if (gap == null) LC() else ConstraintParser.parseLayoutConstraint("gap ${gap}")
  insets?.let {
    lc.insets(it)
  }
  return lc
}

fun JPanel.add(component: Component, vararg constraints: CCFlags) {
  add(component, constraints.create())
}

private fun Array<out CCFlags>.create() = if (isEmpty()) null else CC().apply(this)

// we have to use own class because we want to use method `add`, but Kotlin cannot select proper method implementation (not Kotlin bug, but intentional change)
// and it is required to add invoke operator fun to Component, but use JPanel as receiver
class Panel(layout: LayoutManager) : JPanel(layout) {
  operator fun Component.invoke(vararg constraints: CCFlags) {
    add(this, constraints.create())
  }

  operator fun Component.invoke(constraints: CC? = null) {
    add(this, constraints)
  }

  fun add(component: Component, vararg constraints: CCFlags) {
    add(component, constraints.create())
  }
}

fun LC.apply(flags: Array<out LCFlags>): LC {
  for (flag in flags) {
    when (flag) {
      LCFlags.noGrid -> isNoGrid = true

      LCFlags.flowY -> isFlowX = false

      LCFlags.fill -> fill()
      LCFlags.fillX -> isFillX = true
      LCFlags.fillY -> isFillY = true

      LCFlags.lcWrap -> wrapAfter = 0

      LCFlags.debug -> debug()
    }
  }
  return this
}

fun CC.apply(flags: Array<out CCFlags>): CC {
  for (flag in flags) {
    when (flag) {
      CCFlags.wrap -> isWrap = true
      CCFlags.grow -> grow()
      CCFlags.right -> alignX("right")

      CCFlags.push -> push()
      CCFlags.pushX -> pushX()
      CCFlags.pushY -> pushY()

      CCFlags.span -> span()
      CCFlags.spanX -> spanX()
      CCFlags.spanY -> spanY()

      CCFlags.split -> split()

      CCFlags.skip -> skip()
    }
  }
  return this
}