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
package com.intellij.migLayout

import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.ConstraintParser
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JPanel

fun panel(vararg layoutConstraints: LCFlags) = JPanel(MigLayout(c().apply(layoutConstraints)))

inline fun panel(vararg layoutConstraints: LCFlags, init: JPanel.() -> Unit): JPanel {
  val panel = panel(*layoutConstraints)
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

fun JPanel.label(text: String, constrains: CC? = null, componentStyle: UIUtil.ComponentStyle? = null, fontColor: UIUtil.FontColor? = null) {
  val label = if (componentStyle == null && fontColor == null) {
    JLabel(text)
  }
  else {
    JBLabel(text, componentStyle ?: UIUtil.ComponentStyle.REGULAR, fontColor ?: UIUtil.FontColor.NORMAL)
  }

  add(label, constrains)
}

fun JPanel.hint(text: String, constrains: CC = CC()) {
  if (constrains.horizontal.gapBefore == null) {
    // default gap 10 * indent 3
    constrains.gapLeft("30")
  }
  label(text, constrains, componentStyle = UIUtil.ComponentStyle.SMALL, fontColor = UIUtil.FontColor.BRIGHTER)
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
  add(component, CC().apply(constraints))
}

enum class LCFlags {
  noGrid, flowY, fill
}

enum class CCFlags {
  wrap, grow, push
}

fun LC.apply(flags: Array<out LCFlags>): LC {
  for (flag in flags) {
    when (flag) {
      LCFlags.noGrid -> noGrid()
      LCFlags.flowY -> flowY()
      LCFlags.fill -> fill()
    }
  }
  return this
}

fun CC.apply(flags: Array<out CCFlags>): CC {
  for (flag in flags) {
    when (flag) {
      CCFlags.wrap -> wrap()
      CCFlags.grow -> grow()
      CCFlags.push -> push()
    }
  }
  return this
}