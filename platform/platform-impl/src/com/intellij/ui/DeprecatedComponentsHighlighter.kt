// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("removal", "DEPRECATION")

package com.intellij.ui

import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.progress.util.ColorProgressBar
import com.intellij.openapi.wm.impl.ToolbarComboWidget
import com.intellij.ui.components.labels.ActionLink
import com.intellij.ui.components.labels.SwingActionLink
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.HierarchyEvent
import javax.swing.JComponent

/**
 * Applies [UIUtil.applyDeprecatedBackground] to each shown deprecated component in all windows.
 */
@ApiStatus.Internal
fun installDeprecatedComponentsHighlighter() {
  Toolkit.getDefaultToolkit().addAWTEventListener(AWTEventListener { event ->
    if (event is HierarchyEvent && (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L) {
      val component = event.component
      if (component is JComponent && component.isShowing && isDeprecated(component)) {
        UIUtil.applyDeprecatedBackground(component)
      }
    }
  }, AWTEvent.HIERARCHY_EVENT_MASK)
}

private fun isDeprecated(component: JComponent): Boolean {
  when (component) {
    is SeparatorWithText,
    is ComboboxWithBrowseButton,
    is ActionLink,
    is SwingActionLink,
    is ColorProgressBar,
    is ToolbarComboWidget,
    is JBIntSpinner,
      -> return true
  }

  val layout = component.layout ?: return false
  when (layout) {
    is GridLayoutManager,
    is VerticalLayout,
    is HorizontalLayout,
      -> return true
  }

  // This module does not depend on the MigLayout module
  return layout.javaClass.name == "com.intellij.ui.layout.migLayout.patched.MigLayout"
}
