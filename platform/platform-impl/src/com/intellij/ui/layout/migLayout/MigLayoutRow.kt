// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout.migLayout

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.ui.ComponentWithBrowseButton
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.TextFieldWithHistory
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton
import com.intellij.ui.components.Label
import com.intellij.ui.layout.*
import com.intellij.util.SmartList
import com.intellij.util.ui.JBUI
import net.miginfocom.layout.BoundSize
import net.miginfocom.layout.CC
import net.miginfocom.layout.ConstraintParser
import java.awt.Component
import javax.swing.*
import javax.swing.text.JTextComponent

internal val SHORT_SHORT_TEXT_WIDTH = JBUI.scale(250)
internal val MAX_SHORT_TEXT_WIDTH = JBUI.scale(350)
private val SHORT_TEXT_SIZE: BoundSize = ConstraintParser.parseBoundSize("${SHORT_SHORT_TEXT_WIDTH}px!", false, true)
private val MEDIUM_TEXT_SIZE: BoundSize = ConstraintParser.parseBoundSize("${SHORT_SHORT_TEXT_WIDTH}px::${MAX_SHORT_TEXT_WIDTH}px", false, true)

internal class MigLayoutRow(private val parent: MigLayoutRow?,
                            private val componentConstraints: MutableMap<Component, CC>,
                            override val builder: MigLayoutBuilder,
                            val labeled: Boolean = false,
                            val noGrid: Boolean = false,
                            private val buttonGroup: ButtonGroup? = null,
                            private val indent: Int /* level number (nested rows) */) : Row() {
  val components = SmartList<JComponent>()
  var rightIndex = Int.MAX_VALUE

  internal var subRows: MutableList<MigLayoutRow>? = null
    private set

  var gapAfter: String? = null
    private set

  fun createChildRow(label: JLabel? = null, buttonGroup: ButtonGroup? = null, separated: Boolean = false, noGrid: Boolean = false): MigLayoutRow {
    if (subRows == null) {
      subRows = SmartList()
    }

    val subRows = subRows!!

    if (separated) {
      val row = MigLayoutRow(this, componentConstraints, builder, indent = indent, noGrid = true)
      subRows.add(row)
      row.apply {
        val separatorComponent = SeparatorComponent(0, OnePixelDivider.BACKGROUND, null)
        val cc = CC()
        cc.vertical.gapBefore = gapToBoundSize(VERTICAL_GAP * 3, false)
        cc.vertical.gapAfter = gapToBoundSize(VERTICAL_GAP * 2, false)
        componentConstraints.put(separatorComponent, cc)
        separatorComponent()
      }
    }

    val row = MigLayoutRow(this, componentConstraints, builder, label != null, noGrid = noGrid, indent = indent + computeChildRowIndent(), buttonGroup = buttonGroup)
    subRows.add(row)

    if (label != null) {
      row.apply { label() }
    }

    return row
  }

  private fun computeChildRowIndent(): Int {
    if (components.isEmpty()) {
      return 0
    }
    else {
      val firstComponent = components.first()
      if (firstComponent is JRadioButton || firstComponent is JCheckBox) {
        return ComponentPanelBuilder.computeCommentInsets(firstComponent, true).left
      }
      else {
        return HORIZONTAL_GAP * 3
      }
    }
  }

  override var enabled: Boolean = true
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
    set(value) {
      if (field == value) {
        return
      }

      field = value
      subRows?.forEach { it.enabled = value }
    }

  override var subRowsVisible: Boolean = true
    set(value) {
      if (field == value) {
        return
      }

      field = value
      subRows?.forEach { it.visible = value }
    }

  override operator fun JComponent.invoke(vararg constraints: CCFlags, gapLeft: Int, growPolicy: GrowPolicy?, comment: String?) {
    addComponent(this, constraints, gapLeft, growPolicy, comment)
  }

  // separate method to avoid JComponent as a receiver
  private fun addComponent(component: JComponent,
                           constraints: Array<out CCFlags>? = null,
                           gapLeft: Int = 0,
                           growPolicy: GrowPolicy? = null,
                           comment: String? = null) {
    components.add(component)

    if (comment != null && comment.isNotEmpty()) {
      gapAfter = "0px!"

      // create comment in a new sibling row (developer is still able to create sub rows because rows is not stored in a flat list)
      parent!!.createChildRow().apply {
        val commentComponent = ComponentPanelBuilder.createCommentComponent(comment, true)
        addComponent(commentComponent)

        val commentComponentCC = CC()
        commentComponentCC.horizontal.gapBefore = gapToBoundSize(ComponentPanelBuilder.computeCommentInsets(component, true).left, true)
        componentConstraints.put(commentComponent, commentComponentCC)
      }
    }

    if (buttonGroup != null && component is JToggleButton) {
      buttonGroup.add(component)
    }

    val cc = constraints?.create()?.let { lazyOf(it) } ?: lazy { CC() }
    createComponentConstraints(cc, component, gapLeft = gapLeft, growPolicy = growPolicy)

    if (!noGrid && indent > 0 && components.size == 1) {
      cc.value.horizontal.gapBefore = gapToBoundSize(indent, true)
    }

    if (cc.isInitialized()) {
      componentConstraints.put(component, cc.value)
    }
  }

  override fun alignRight() {
    if (rightIndex != Int.MAX_VALUE) {
      throw IllegalStateException("right allowed only once")
    }
    rightIndex = components.size
  }

  override fun createRow(label: String?): Row {
    return createChildRow(label = label?.let { Label(it) })
  }
}

private fun createComponentConstraints(cc: Lazy<CC>,
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

    component is JPanel && component.componentCount == 1 &&
    (component.getComponent(0) as? JComponent)?.getClientProperty(ActionToolbar.ACTION_TOOLBAR_PROPERTY_KEY) != null -> {
      cc.value.grow().push()
    }
  }
}

private fun applyGrowPolicy(cc: CC, growPolicy: GrowPolicy) {
  cc.horizontal.size = when (growPolicy) {
    GrowPolicy.SHORT_TEXT -> SHORT_TEXT_SIZE
    GrowPolicy.MEDIUM_TEXT -> MEDIUM_TEXT_SIZE
  }
}