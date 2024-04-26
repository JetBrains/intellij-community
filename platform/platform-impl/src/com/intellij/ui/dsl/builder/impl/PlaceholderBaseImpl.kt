// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.UiSwitcher
import com.intellij.ui.dsl.builder.CellBase
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.ui.dsl.gridLayout.Constraints
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JLabel

@ApiStatus.Internal
internal abstract class PlaceholderBaseImpl<T : CellBase<T>>(private val parent: RowImpl) : CellBaseImpl<T>() {

  private var placeholderCellData: PlaceholderCellData? = null
  private var visible = true
  private var enabled = true
  private val uiSwitchers = LinkedHashSet<UiSwitcher>()
  private var componentField: JComponent? = null
  private var label: JLabel? = null

  var component: JComponent?
    get() = componentField
    set(value) {
      if (componentField !== value) {
        removeComponent()
        if (value != null) {
          value.isVisible = value.isVisible && visible && parent.isVisible()
          value.isEnabled = value.isEnabled && enabled && parent.isEnabled()

          componentField = value

          if (placeholderCellData != null) {
            initInstalledComponent()
          }

          label?.labelFor = value
        }
      }
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
    return this
  }

  open fun init(panel: DialogPanel, constraints: Constraints, spacing: SpacingConfiguration) {
    placeholderCellData = PlaceholderCellData(panel, constraints, spacing)
    if (componentField != null) {
      initInstalledComponent()
    }
  }

  private fun removeComponent() {
    val installedComponent = componentField

    if (installedComponent == null) {
      return
    }

    componentField = null

    UiSwitcher.removeAll(installedComponent, uiSwitchers)

    placeholderCellData?.let {
      if (installedComponent is DialogPanel) {
        it.panel.unregisterIntegratedPanel(installedComponent)
      }
      it.panel.remove(installedComponent)
      invalidate()
    }

    label?.labelFor = null
  }

  private fun initInstalledComponent() {
    checkNotNull(placeholderCellData)
    val installedComponent = checkNotNull(componentField)

    UiSwitcher.appendAll(installedComponent, uiSwitchers)

    placeholderCellData?.let {
      val gaps = customGaps ?: getComponentGaps(it.constraints.gaps.left, it.constraints.gaps.right, installedComponent, it.spacing)
      it.constraints = it.constraints.copy(
        gaps = gaps,
        visualPaddings = prepareVisualPaddings(installedComponent)
      )
      it.panel.add(installedComponent, it.constraints)
      if (installedComponent is DialogPanel) {
        it.panel.registerIntegratedPanel(installedComponent)
      }

      invalidate()
    }
  }

  fun appendUiSwitcher(uiSwitcher: UiSwitcher) {
    uiSwitchers.add(uiSwitcher)
    component?.let {
      UiSwitcher.append(it, uiSwitcher)
    }
  }

  fun initLabelFor(label: JLabel) {
    this.label = label
    component?.let {
      label.labelFor = it
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

private data class PlaceholderCellData(val panel: DialogPanel, var constraints: Constraints, val spacing: SpacingConfiguration)
