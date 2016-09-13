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
import net.miginfocom.layout.BoundSize
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
internal const val VERTICAL_GAP = 5

// default values differs to MigLayout - IntelliJ Platform defaults are used
// see com.intellij.uiDesigner.core.AbstractLayout.DEFAULT_HGAP and DEFAULT_VGAP (multiplied by 2 to achieve the same look (it seems in terms of MigLayout gap is both left and right space))
fun c(insets: String? = "0", gap: String? = "20 $VERTICAL_GAP"): LC {
  // no setter for gap, so, create string to parse
  val lc = if (gap == null) LC() else ConstraintParser.parseLayoutConstraint("gap ${gap}")
  insets?.let {
    lc.insets(it)
  }
  return lc
}

// do not use directly
inline fun createUnsafePanel(title: String? = null, wrap: Int = 0, layoutConstraints: Array<out LCFlags>, init: Panel.() -> Unit): JPanel {
  val constraints = c().apply(layoutConstraints)
  if (wrap != 0) {
    constraints.wrapAfter(wrap)
  }

  val panel = Panel(MigLayout(constraints))
  if (title != null) {
    setTitledBorder(title, panel)
  }

  panel.init()
  return panel
}

inline fun createPanel2(title: String?, init: LayoutBuilder.() -> Unit): JPanel {
  val builder = createLayoutBuilder()
  builder.init()

  val panel = com.intellij.ui.components.Panel(title, MigLayout(c().fillX()))
  builder.`$`.build(panel)
  return panel
}

fun createLayoutBuilder() = LayoutBuilder(MigLayoutBuilder())

fun setTitledBorder(title: String, panel: JPanel) {
  val border = IdeBorderFactory.createTitledBorder(title, false)
  panel.border = border
  border.acceptMinimumSize(panel)
}

// we have to use own class because we want to use method `add`, but Kotlin cannot select proper method implementation (not Kotlin bug, but intentional change)
// and it is required to add invoke operator fun to Component, but use JPanel as receiver
class Panel(layout: LayoutManager) : JPanel(layout) {
  internal var beforeAdd: ((Component, CC?) -> CC?)? = null

  operator fun Component.invoke(vararg constraints: CCFlags) {
    add(this, constraints.create())
  }

  operator fun Component.invoke(constraints: CC? = null) {
    add(this, constraints)
  }

  fun add(component: Component, vararg constraints: CCFlags) {
    add(component, constraints.create())
  }

  override fun addImpl(component: Component, constraints: Any?, index: Int) {
    super.addImpl(component, beforeAdd?.invoke(component, constraints as? CC) ?: constraints, index)
  }
}

fun Array<out CCFlags>.create() = if (isEmpty()) null else CC().apply(this)

fun CC.apply(flags: Array<out CCFlags>): CC {
  for (flag in flags) {
    when (flag) {
      CCFlags.wrap -> isWrap = true
      CCFlags.grow -> grow()

    // If you have more than one component in a cell the alignment keywords will not work since the behavior would be indeterministic.
    // You can however accomplish the same thing by setting a gap before and/or after the components.
    // That gap may have a minimum size of 0 and a preferred size of a really large value to create a "pushing" gap.
    // There is even a keyword for this: "push". So "gapleft push" will be the same as "align right" and work for multi-component cells as well.
      CCFlags.right -> horizontal.gapBefore = BoundSize(null, null, null, true, null)

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

fun JPanel.add(component: Component, vararg constraints: CCFlags) {
  add(component, constraints.create())
}