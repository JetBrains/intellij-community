// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout.migLayout

import com.intellij.ui.components.Label
import com.intellij.ui.layout.*
import com.intellij.util.SmartList
import net.miginfocom.layout.BoundSize
import net.miginfocom.layout.CC
import net.miginfocom.layout.ConstraintParser
import java.awt.Component
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JToggleButton

private val SHORT_TEXT_SIZE: BoundSize = ConstraintParser.parseBoundSize("250!", false, true)
private val MEDIUM_TEXT_SIZE: BoundSize = ConstraintParser.parseBoundSize("250::350", false, true)

internal class MigLayoutRow(private val componentConstraints: MutableMap<Component, CC>,
                            override val builder: MigLayoutBuilder,
                            val labeled: Boolean = false,
                            val noGrid: Boolean = false,
                            private val buttonGroup: ButtonGroup? = null,
                            val separated: Boolean = false) : Row() {
  val components = SmartList<JComponent>()
  var rightIndex = Int.MAX_VALUE

  internal var _subRows: MutableList<MigLayoutRow>? = null

  override val subRows: List<Row>
    get() = _subRows ?: emptyList()

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
      _subRows?.forEach { it.enabled = value }
    }

  override var subRowsVisible: Boolean = true
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

  private fun addComponent(component: JComponent, constraints: Array<out CCFlags>, gapLeft: Int, growPolicy: GrowPolicy?) {
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
    if (_subRows == null) {
      _subRows = SmartList()
    }
    return builder.newRow(rowList = _subRows!!, label = label?.let { Label(it) })
  }
}

private fun createComponentConstraints(constraints: Array<out CCFlags>? = null,
                                       gapLeft: Int = 0,
                                       gapAfter: Int = 0,
                                       gapTop: Int = 0,
                                       gapBottom: Int = 0,
                                       split: Int = -1,
                                       growPolicy: GrowPolicy?): CC? {
  @Suppress("LocalVariableName")
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

  if (growPolicy != null) {
    applyGrowPolicy(cc(), growPolicy)
  }

  return _cc
}

internal fun applyGrowPolicy(cc: CC, growPolicy: GrowPolicy) {
  cc.horizontal.size = when (growPolicy) {
    GrowPolicy.SHORT_TEXT -> SHORT_TEXT_SIZE
    GrowPolicy.MEDIUM_TEXT -> MEDIUM_TEXT_SIZE
  }
}