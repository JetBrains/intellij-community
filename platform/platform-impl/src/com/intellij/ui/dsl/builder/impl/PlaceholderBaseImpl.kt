// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.INTEGRATED_PANEL_PROPERTY
import com.intellij.ui.dsl.builder.CellBase
import com.intellij.ui.dsl.gridLayout.Constraints
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
internal abstract class PlaceholderBaseImpl<T : CellBase<T>>(private val parent: RowImpl) : CellBaseImpl<T>() {

  var panel: DialogPanel? = null
  private var constraints: Constraints? = null

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

  fun init(panel: DialogPanel, constraints: Constraints) {
    this.panel = panel
    this.constraints = constraints

    if (component != null) {
      reinstallComponent(null, component)
    }
  }

  private fun reinstallComponent(oldComponent: JComponent?, newComponent: JComponent?) {
    var invalidate = false
    if (oldComponent != null) {
      panel?.remove(oldComponent)
      invalidate = true
    }

    if (newComponent != null) {
      if (newComponent is DialogPanel) {
        newComponent.putClientProperty(INTEGRATED_PANEL_PROPERTY, true)
      }
      newComponent.isVisible = visible && parent.isVisible()
      newComponent.isEnabled = enabled && parent.isEnabled()
      constraints?.visualPaddings = getVisualPaddings(newComponent.origin)
      panel?.add(newComponent, constraints)
      invalidate = true
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
    panel?.let {
      // Force parent to re-layout
      it.revalidate()
      it.repaint()
    }
  }
}
