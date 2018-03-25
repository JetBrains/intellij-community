// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout.migLayout

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.TextFieldWithHistory
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import net.miginfocom.layout.BoundSize
import net.miginfocom.layout.CC
import net.miginfocom.layout.ConstraintParser
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JScrollPane
import javax.swing.text.JTextComponent

internal fun Array<out CCFlags>.create() = if (isEmpty()) null else CC().apply(this)

private val SHORT_SHORT_TEXT_WIDTH = JBUI.scale(250)
private val MAX_SHORT_TEXT_WIDTH = JBUI.scale(350)
private val SHORT_TEXT_SIZE: BoundSize = ConstraintParser.parseBoundSize("${SHORT_SHORT_TEXT_WIDTH}px!", false, true)
private val MEDIUM_TEXT_SIZE: BoundSize = ConstraintParser.parseBoundSize("${SHORT_SHORT_TEXT_WIDTH}px::${MAX_SHORT_TEXT_WIDTH}px", false, true)

private fun CC.apply(flags: Array<out CCFlags>): CC {
  for (flag in flags) {
    when (flag) {
      //CCFlags.wrap -> isWrap = true
      CCFlags.grow -> grow()
      CCFlags.growX -> {
        growX()
      }
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

internal fun createComponentConstraints(cc: Lazy<CC>,
                                       component: Component,
                                       gapLeft: Int = 0,
                                       gapAfter: Int = 0,
                                       gapTop: Int = 0,
                                       gapBottom: Int = 0,
                                       split: Int = -1,
                                       growPolicy: GrowPolicy?): CC? {
  if (gapLeft != 0) {
    cc.value.horizontal.gapBefore = gapToBoundSize(gapLeft, true)
  }
  if (gapAfter != 0) {
    cc.value.horizontal.gapAfter = gapToBoundSize(gapAfter, true)
  }

  if (gapTop != 0) {
    cc.value.vertical.gapBefore = gapToBoundSize(gapTop, false)
  }
  if (gapBottom != 0) {
    cc.value.vertical.gapAfter = gapToBoundSize(gapBottom, false)
  }

  if (split != -1) {
    cc.value.split = split
  }

  if (growPolicy != null) {
    applyGrowPolicy(cc.value, growPolicy)
  }
  else {
    addGrowIfNeed(cc, component)
  }

  return if (cc.isInitialized()) cc.value else null
}

private fun addGrowIfNeed(cc: Lazy<CC>, component: Component) {
  when {
    component is TextFieldWithHistory || component is TextFieldWithHistoryWithBrowseButton -> {
      // yes, no max width. approved by UI team (all path fields stretched to the width of the window)
      cc.value.minWidth("${MAX_SHORT_TEXT_WIDTH}px")
      cc.value.growX()
    }

    component is JPasswordField -> {
      applyGrowPolicy(cc.value, GrowPolicy.SHORT_TEXT)
    }

    component is JTextComponent || component is SeparatorComponent || component is ComponentWithBrowseButton<*> -> {
      cc.value.growX()
    }

    component is JScrollPane ||
    (component is JPanel && component.componentCount == 1 && (component.getComponent(0) as? JComponent)?.getClientProperty(
      ActionToolbar.ACTION_TOOLBAR_PROPERTY_KEY) != null) -> {
      cc.value
        .grow()
        .push()
    }
  }
}

private fun applyGrowPolicy(cc: CC, growPolicy: GrowPolicy) {
  cc.horizontal.size = when (growPolicy) {
    GrowPolicy.SHORT_TEXT -> SHORT_TEXT_SIZE
    GrowPolicy.MEDIUM_TEXT -> MEDIUM_TEXT_SIZE
  }
}