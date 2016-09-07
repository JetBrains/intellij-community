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

import com.intellij.ui.IdeBorderFactory
import net.miginfocom.layout.CC
import net.miginfocom.layout.ConstraintParser
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.Component
import java.awt.LayoutManager
import javax.swing.JPanel

// see com.intellij.uiDesigner.core.AbstractLayout.DEFAULT_HGAP and DEFAULT_VGAP
// https://docs.google.com/document/d/1DKnLkO-7_onA7_NCw669aeMH5ltNvw-QMiQHnXu8k_Y/edit

internal const val GAP = 10

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

// do not use directly
inline fun createPanel(title: String?, layoutConstraints: Array<out LCFlags>, init: Panel.() -> Unit): JPanel {
  val panel = Panel(MigLayout(c().apply(layoutConstraints)))
  if (title != null) {
    setTitledBorder(title, panel)
  }

  panel.init()
  return panel
}

fun setTitledBorder(title: String, panel: JPanel) {
  val border = IdeBorderFactory.createTitledBorder(title, false)
  panel.border = border
  border.acceptMinimumSize(panel)
}

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

fun Array<out CCFlags>.create() = if (isEmpty()) null else CC().apply(this)

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