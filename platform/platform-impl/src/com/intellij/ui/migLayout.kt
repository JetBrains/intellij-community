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

val DEFAULT_PANEL_LC = c()

fun panel(layoutConstraints: LC? = DEFAULT_PANEL_LC) = JPanel(MigLayout(layoutConstraints))

inline fun panel(layoutConstraints: LC? = DEFAULT_PANEL_LC, init: JPanel.() -> Unit): JPanel {
  val panel = panel(layoutConstraints)
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

fun JPanel.titledPanel(title: String, wrappedComponent: Component, constrains: CC? = null) {
  val panel = titledPanel(title)
  panel.add(wrappedComponent)
  add(panel, constrains)
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
fun c(insets: String? = "0", gap: String? = "20 5", fill: Boolean = false, noGrid: Boolean = false, flowY: Boolean = false): LC {
  // no setter for gap, so, create string to parse
  val lc = if (gap == null) LC() else ConstraintParser.parseLayoutConstraint("gap ${gap}")
  insets?.let {
    lc.insets(it)
  }
  if (fill) {
    lc.fill()
  }
  if (noGrid) {
    lc.noGrid()
  }
  if (flowY) {
    lc.flowY()
  }
  return lc
}

@Suppress("unused")
// add receiver to reduce completion scope
fun JPanel.c(grow: Boolean = false, push: Boolean = false): CC {
  val cc = CC()
  if (grow) {
    cc.grow()
  }
  if (push) {
    cc.push()
  }
  return cc
}