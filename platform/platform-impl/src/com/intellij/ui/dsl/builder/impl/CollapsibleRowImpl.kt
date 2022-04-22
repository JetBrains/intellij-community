// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.UiSwitcher
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.Expandable
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import javax.swing.JComponent
import javax.swing.border.EmptyBorder

@ApiStatus.Internal
internal class CollapsibleRowImpl(dialogPanelConfig: DialogPanelConfig,
                                  panelContext: PanelContext,
                                  parent: PanelImpl,
                                  @NlsContexts.BorderTitle title: String,
                                  init: Panel.() -> Unit) :
  RowImpl(dialogPanelConfig, panelContext, parent, RowLayout.INDEPENDENT), CollapsibleRow {

  private val collapsibleTitledSeparator = CollapsibleTitledSeparator(title)

  override var expanded by collapsibleTitledSeparator::expanded
  
  override fun setText(@NlsContexts.Separator text: String) {
    collapsibleTitledSeparator.text = text
  }
  
  override fun addExpandedListener(action: (Boolean) -> Unit) {
    collapsibleTitledSeparator.expandedProperty.afterChange { action(it) }
  }

  init {
    collapsibleTitledSeparator.setLabelFocusable(true)
    (collapsibleTitledSeparator.label.border as? EmptyBorder)?.borderInsets?.let {
      collapsibleTitledSeparator.putClientProperty(DslComponentProperty.VISUAL_PADDINGS,
                                                   Gaps(top = it.top, left = it.left, bottom = it.bottom))
    }

    val expandable = ExpandableImpl(collapsibleTitledSeparator)
    collapsibleTitledSeparator.label.putClientProperty(Expandable::class.java, expandable)

    val action = DumbAwareAction.create { expanded = !expanded }
    action.registerCustomShortcutSet(ActionUtil.getShortcutSet("CollapsiblePanel-toggle"), collapsibleTitledSeparator.label)

    val collapsibleTitledSeparator = this.collapsibleTitledSeparator
    panel {
      row {
        cell(collapsibleTitledSeparator)
          .horizontalAlign(HorizontalAlign.FILL)
      }
      val expandablePanel = panel(init)
      collapsibleTitledSeparator.onAction(expandablePanel::visible)
      collapsibleTitledSeparator.putUserData(UiSwitcher.UI_SWITCHER, UiSwitcherImpl(expandable, expandablePanel))
    }
  }

  private class ExpandableImpl(private val separator: CollapsibleTitledSeparator) : Expandable {
    override fun expand() {
      separator.expanded = true
    }

    override fun collapse() {
      separator.expanded = false
    }

    override fun isExpanded(): Boolean {
      return separator.expanded
    }
  }

  private class UiSwitcherImpl(
    private val expandable: Expandable,
    private val panel: Panel
  ) : UiSwitcher {
    override fun show(component: Component) {
      if (component is JComponent) {
        val hierarchy = component.getUserData(DSL_PANEL_HIERARCHY)
        if (hierarchy != null && panel in hierarchy) {
          expandable.expand()
        }
      }
    }
  }
}
