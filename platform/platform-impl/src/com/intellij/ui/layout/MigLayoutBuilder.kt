/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInspection.SmartHashMap
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.TextFieldWithHistory
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton
import com.intellij.ui.components.Label
import com.intellij.ui.components.noteComponent
import com.intellij.util.SmartList
import net.miginfocom.layout.*
import net.miginfocom.swing.MigLayout
import java.awt.Component
import java.awt.Container
import javax.swing.*
import javax.swing.text.JTextComponent

/**
 * Automatically add `growX` to JTextComponent (see isAddGrowX).
 * Automatically add `grow` and `push` to JPanel (see isAddGrowX).
 */
internal class MigLayoutBuilder : LayoutBuilderImpl {
  private val rows = SmartList<MigLayoutRow>()

  private val componentConstraints: MutableMap<Component, CC> = SmartHashMap()

  override fun newRow(label: JLabel?, buttonGroup: ButtonGroup?, separated: Boolean, indented: Boolean): Row {
    if (separated) {
      val row = MigLayoutRow(componentConstraints, this, noGrid = true, separated = true)
      rows.add(row)
      row.apply { SeparatorComponent(0, OnePixelDivider.BACKGROUND, null)() }
    }

    val row = MigLayoutRow(componentConstraints, this, label != null, buttonGroup = buttonGroup, indented = indented)
    rows.add(row)

    label?.let { row.apply { label() } }

    return row
  }

  override fun noteRow(text: String) {
    // add empty row as top gap
    newRow()

    val row = MigLayoutRow(componentConstraints, this, noGrid = true)
    rows.add(row)
    row.apply { noteComponent(text)() }
  }

  override fun build(container: Container, layoutConstraints: Array<out LCFlags>) {
    val labeled = rows.firstOrNull({ it.labeled && !it.indented }) != null
    var gapTop = -1

    val lc = c()
    if (layoutConstraints.isEmpty()) {
      lc.fillX()
      // not fillY because it leads to enormously large cells - we use cc `push` in addition to cc `grow` as a more robust and easy solution
    }
    else {
      lc.apply(layoutConstraints)
    }

    lc.noVisualPadding()
    lc.hideMode = 3

    container.layout = MigLayout(lc)

    val noGrid = layoutConstraints.contains(LCFlags.noGrid)

    for (row in rows) {
      val lastComponent = row.components.lastOrNull()
      if (lastComponent == null) {
        if (row === rows.first()) {
          // do not add gap for the first row
          continue
        }

        // https://goo.gl/LDylKm
        // gap = 10u where u = 4px
        gapTop = VERTICAL_GAP * 3
      }

      var isSplitRequired = true
      for ((index, component) in row.components.withIndex()) {
        // MigLayout in any case always creates CC, so, create instance even if it is not required
        val cc = componentConstraints.get(component) ?: CC()

        if (gapTop != -1) {
          cc.vertical.gapBefore = gapToBoundSize(gapTop, false)
          gapTop = -1
        }

        addGrowIfNeed(cc, component)

        if (!noGrid) {
          if (component === lastComponent) {
            isSplitRequired = false
            cc.wrap()
          }

          if (row.noGrid) {
            if (component === row.components.first()) {
              // rowConstraints.noGrid() doesn't work correctly
              cc.spanX()
              if (row.separated) {
                cc.vertical.gapBefore = gapToBoundSize(VERTICAL_GAP * 3, false)
                cc.vertical.gapAfter = gapToBoundSize(VERTICAL_GAP * 2, false)
              }
            }
          }
          else {
            var isSkippableComponent = true
            if (component === row.components.first()) {
              if (row.indented) {
                cc.horizontal.gapBefore = gapToBoundSize(HORIZONTAL_GAP * 3, true)
              }

              if (labeled) {
                if (row.labeled) {
                  isSkippableComponent = false
                }
                else {
                  cc.skip()
                }
              }
            }

            if (isSkippableComponent) {
              if (isSplitRequired) {
                isSplitRequired = false
                cc.split()
              }

              // do not add gap if next component is gear action button
              if (component !== lastComponent && !row.components.get(index + 1).let { it is JLabel && it.icon === AllIcons.General.Gear }) {
                cc.horizontal.gapAfter = gapToBoundSize(HORIZONTAL_GAP * 2, true)
              }
            }
          }

          if (index >= row.rightIndex) {
            cc.horizontal.gapBefore = BoundSize(null, null, null, true, null)
          }
        }

        container.add(component, cc)
      }
    }

    // do not hold components
    componentConstraints.clear()
  }
}

private fun addGrowIfNeed(cc: CC, component: Component) {
  if (component is TextFieldWithHistory || component is TextFieldWithHistoryWithBrowseButton) {
    cc.minWidth("350")
    cc.growX()
  }
  else if (component is JTextComponent || component is SeparatorComponent || component is ComponentWithBrowseButton<*>) {
    cc.growX()
  }
  else if (component is JPanel && component.componentCount == 1 &&
           (component.getComponent(0) as? JComponent)?.getClientProperty(ActionToolbar.ACTION_TOOLBAR_PROPERTY_KEY) != null) {
    cc.grow().push()
  }
}

private class MigLayoutRow(private val componentConstraints: MutableMap<Component, CC>,
                           override val builder: LayoutBuilderImpl,
                           val labeled: Boolean = false,
                           val noGrid: Boolean = false,
                           private val buttonGroup: ButtonGroup? = null,
                           val separated: Boolean = false,
                           val indented: Boolean = false) : Row() {
  val components = SmartList<Component>()
  var rightIndex = Int.MAX_VALUE

  private var _subRows: MutableList<Row>? = null

  override val subRows: List<Row>
    get() = _subRows ?: emptyList()

  override var enabled: Boolean = true
    get() = field
    set(value) {
      if (field == value) {
        return
      }

      field = value
      for (c in components) {
        c.isEnabled = value
      }
    }

  override var visible: Boolean = true
    get() = field
    set(value) {
      if (field == value) {
        return
      }

      field = value
      for (c in components) {
        c.isVisible = value
      }
    }

  override var subRowsEnabled: Boolean = true
    get() = field
    set(value) {
      if (field == value) {
        return
      }

      field = value
      _subRows?.forEach { it.enabled = value }
    }

  override var subRowsVisible: Boolean = true
    get() = field
    set(value) {
      if (field == value) {
        return
      }

      field = value
      _subRows?.forEach { it.visible = value }
    }

  override operator fun JComponent.invoke(vararg constraints: CCFlags, gapLeft: Int, growPolicy: GrowPolicy?) {
    addComponent(this, constraints, gapLeft = gapLeft, growPolicy = growPolicy)
  }

  private fun addComponent(component: Component, constraints: Array<out CCFlags>, gapLeft: Int, growPolicy: GrowPolicy?) {
    if (buttonGroup != null && component is JToggleButton) {
      buttonGroup.add(component)
    }

    createComponentConstraints(constraints, gapLeft = gapLeft, growPolicy = growPolicy)?.let {
      componentConstraints.put(component, it)
    }
    components.add(component)
  }

  override fun alignRight() {
    if (rightIndex != Int.MAX_VALUE) {
      throw IllegalStateException("right allowed only once")
    }
    rightIndex = components.size
  }

  override fun createRow(label: String?): Row {
    val row = builder.newRow(label?.let { Label(it) }, indented = true)
    if (_subRows == null) {
      _subRows = SmartList()
    }
    _subRows!!.add(row)
    return row
  }
}

private fun createComponentConstraints(constraints: Array<out CCFlags>? = null,
                                       gapLeft: Int = 0,
                                       gapAfter: Int = 0,
                                       gapTop: Int = 0,
                                       gapBottom: Int = 0,
                                       split: Int = -1,
                                       growPolicy: GrowPolicy?): CC? {
  var _cc = constraints?.create()
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

  if (growPolicy == GrowPolicy.SHORT_TEXT) {
    cc().maxWidth("210")
  }
  else if (growPolicy == GrowPolicy.MEDIUM_TEXT) {
    cc().minWidth("210")
    cc().maxWidth("350")
  }

  return _cc
}

private fun gapToBoundSize(value: Int, isHorizontal: Boolean): BoundSize {
  val unitValue = UnitValue(value.toFloat(), "", isHorizontal, UnitValue.STATIC, null)
  return BoundSize(unitValue, unitValue, null, false, null)
}

// default values differs to MigLayout - IntelliJ Platform defaults are used
// see com.intellij.uiDesigner.core.AbstractLayout.DEFAULT_HGAP and DEFAULT_VGAP (multiplied by 2 to achieve the same look (it seems in terms of MigLayout gap is both left and right space))
private fun c(insets: String? = "0", gridGapX: Int = HORIZONTAL_GAP * 2, gridGapY: Int = VERTICAL_GAP): LC {
  // no setter for gap, so, create string to parse
  val lc = LC()
  lc.gridGapX = gapToBoundSize(gridGapX, true)
  lc.gridGapY = gapToBoundSize(gridGapY, false)
  insets?.let {
    lc.insets(it)
  }
  return lc
}

private fun Array<out CCFlags>.create() = if (isEmpty()) null else CC().apply(this)

private fun CC.apply(flags: Array<out CCFlags>): CC {
  for (flag in flags) {
    when (flag) {
      //CCFlags.wrap -> isWrap = true
      CCFlags.grow -> grow()
      CCFlags.growX -> growX()
      CCFlags.growY -> growY()

    // If you have more than one component in a cell the alignment keywords will not work since the behavior would be indeterministic.
    // You can however accomplish the same thing by setting a gap before and/or after the components.
    // That gap may have a minimum size of 0 and a preferred size of a really large value to create a "pushing" gap.
    // There is even a keyword for this: "push". So "gapleft push" will be the same as "align right" and work for multi-component cells as well.
      //CCFlags.right -> horizontal.gapBefore = BoundSize(null, null, null, true, null)

      CCFlags.push -> push()
      CCFlags.pushX -> pushX()
      CCFlags.pushY -> pushY()

      //CCFlags.span -> span()
      //CCFlags.spanX -> spanX()
      //CCFlags.spanY -> spanY()

      //CCFlags.split -> split()

      //CCFlags.skip -> skip()
    }
  }
  return this
}

private fun LC.apply(flags: Array<out LCFlags>): LC {
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

private class DebugMigLayoutAction : ToggleAction(), DumbAware {
  private var debugEnabled = false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    debugEnabled = state
    LayoutUtil.setGlobalDebugMillis(if (debugEnabled) 300 else 0)
  }

  override fun isSelected(e: AnActionEvent) = debugEnabled
}