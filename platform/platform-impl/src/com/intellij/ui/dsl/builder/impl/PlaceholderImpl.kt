// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.INTEGRATED_PANEL_PROPERTY
import com.intellij.ui.dsl.builder.Placeholder
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.gridLayout.Constraints
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
internal class PlaceholderImpl(private val parent: RowImpl) : CellBaseImpl<Placeholder>(), Placeholder {

  private var panel: DialogPanel? = null
  private var constraints: Constraints? = null

  private var visible = true
  private var enabled = true

  override var component: JComponent? = null
    set(value) {
      reinstallComponent(field, value)
      field = value
    }

  override fun horizontalAlign(horizontalAlign: HorizontalAlign): Placeholder {
    super.horizontalAlign(horizontalAlign)
    return this
  }

  override fun verticalAlign(verticalAlign: VerticalAlign): Placeholder {
    super.verticalAlign(verticalAlign)
    return this
  }

  override fun resizableColumn(): Placeholder {
    super.resizableColumn()
    return this
  }

  override fun gap(rightGap: RightGap): Placeholder {
    super.gap(rightGap)
    return this
  }

  override fun enabledFromParent(parentEnabled: Boolean) {
    doEnabled(parentEnabled && enabled)
  }

  override fun enabled(isEnabled: Boolean): Placeholder {
    enabled = isEnabled
    if (parent.isEnabled()) {
      doEnabled(enabled)
    }
    return this
  }

  override fun visibleFromParent(parentVisible: Boolean) {
    doVisible(parentVisible && visible)
  }

  override fun visible(isVisible: Boolean): Placeholder {
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
    if (oldComponent != null) {
      panel?.remove(oldComponent)
    }

    if (newComponent != null) {
      if (newComponent is DialogPanel) {
        newComponent.putClientProperty(INTEGRATED_PANEL_PROPERTY, true)
      }
      newComponent.isVisible = visible
      newComponent.isEnabled = enabled
      panel?.add(newComponent, constraints)
    }
  }

  private fun doVisible(isVisible: Boolean) {
    component?.let {
      if (it.isVisible != isVisible) {
        it.isVisible = isVisible
        // Force parent to re-layout
        it.parent?.revalidate()
      }
    }
  }

  private fun doEnabled(isEnabled: Boolean) {
    component?.let {
      it.isEnabled = isEnabled
    }
  }
}
