// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.CellBase
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.ui.dsl.gridLayout.Constraints
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
internal abstract class PlaceholderBaseImpl<T : CellBase<T>>(private val parent: RowImpl) : CellBaseImpl<T>() {

  protected var placeholderCellData: PlaceholderCellData? = null
    private set

  private var visible = true
  private var enabled = true

  var component: JComponent? = null
    set(value) {
      reinstallComponent(field, value)
      field = value
    }

  override fun enabledFromParent(parentEnabled: Boolean) {
    doEnabled(parentEnabled && enabled)
  }

  override fun enabled(isEnabled: Boolean): CellBase<T> {
    enabled = isEnabled
    if (parent.isEnabled()) {
      doEnabled(enabled)
    }
    return this
  }

  override fun visibleFromParent(parentVisible: Boolean) {
    doVisible(parentVisible && visible)
  }

  override fun visible(isVisible: Boolean): CellBase<T> {
    visible = isVisible
    if (parent.isVisible()) {
      doVisible(visible)
    }
    component?.isVisible = isVisible
    return this
  }

  open fun init(panel: DialogPanel, constraints: Constraints, spacing: SpacingConfiguration) {
    placeholderCellData = PlaceholderCellData(panel, constraints, spacing)

    if (component != null) {
      reinstallComponent(null, component)
    }
  }

  private fun reinstallComponent(oldComponent: JComponent?, newComponent: JComponent?) {
    var invalidate = false
    if (oldComponent != null) {
      placeholderCellData?.let {
        if (oldComponent is DialogPanel) {
          it.panel.unregisterSubPanel(oldComponent)
        }
        it.panel.remove(oldComponent)
        invalidate = true
      }
    }

    if (newComponent != null) {
      newComponent.isVisible = visible && parent.isVisible()
      newComponent.isEnabled = enabled && parent.isEnabled()
      placeholderCellData?.let {
        val gaps = customGaps ?: getComponentGaps(it.constraints.gaps.left, it.constraints.gaps.right, newComponent, it.spacing)
        it.constraints = it.constraints.copy(
          gaps = gaps,
          visualPaddings = getVisualPaddings(newComponent.origin)
        )
        it.panel.add(newComponent, it.constraints)
        if (newComponent is DialogPanel) {
          it.panel.registerSubPanel(newComponent)
        }
        invalidate = true
      }
    }

    if (invalidate) {
      invalidate()
    }
  }

  private fun doVisible(isVisible: Boolean) {
    component?.let {
      if (it.isVisible != isVisible) {
        it.isVisible = isVisible
        invalidate()
      }
    }
  }

  private fun doEnabled(isEnabled: Boolean) {
    component?.let {
      it.isEnabled = isEnabled
    }
  }

  private fun invalidate() {
    placeholderCellData?.let {
      // Force parent to re-layout
      it.panel.revalidate()
      it.panel.repaint()
    }
  }
}

@ApiStatus.Internal
internal data class PlaceholderCellData(val panel: DialogPanel, var constraints: Constraints, val spacing: SpacingConfiguration)
