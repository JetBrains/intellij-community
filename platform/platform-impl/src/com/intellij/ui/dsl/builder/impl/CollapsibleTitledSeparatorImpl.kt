// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.IndentedIcon
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Cursor
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleState
import kotlin.math.max

// todo move to components package, rename into CollapsibleTitledSeparator, make internal
@ApiStatus.Internal
class CollapsibleTitledSeparatorImpl(@NlsContexts.Separator title: String) : TitledSeparator(title) {
  val expandedProperty: AtomicBooleanProperty = AtomicBooleanProperty(true)
  var expanded: Boolean by expandedProperty

  init {
    updateIcon()
    expandedProperty.afterChange { updateIcon() }
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    addMouseListener(object : MouseAdapter() {
      override fun mouseReleased(e: MouseEvent) {
        expanded = !expanded
      }
    })
  }

  fun onAction(listener: (Boolean) -> Unit) {
    expandedProperty.afterChange(listener)
  }

  override fun createLabel(): JBLabel = object : JBLabel() {
    override fun getAccessibleContext(): AccessibleContext? {
      if (accessibleContext == null) {
        accessibleContext = object : AccessibleJLabel() {
          override fun getAccessibleStateSet() =
            super.getAccessibleStateSet().apply {
              add(AccessibleState.EXPANDABLE)
              add(if (expanded) AccessibleState.EXPANDED else AccessibleState.COLLAPSED)
            }
        }
      }
      return accessibleContext
    }
  }

  private fun updateIcon() {
    val treeExpandedIcon = UIUtil.getTreeExpandedIcon()
    val treeCollapsedIcon = UIUtil.getTreeCollapsedIcon()
    val width = max(treeExpandedIcon.iconWidth, treeCollapsedIcon.iconWidth)
    var icon = if (expanded) treeExpandedIcon else treeCollapsedIcon
    val extraSpace = width - icon.iconWidth
    if (extraSpace > 0) {
      val left = extraSpace / 2
      icon = IndentedIcon(icon, Insets(0, left, 0, extraSpace - left))
    }
    label.icon = icon
    label.disabledIcon = IconLoader.getTransparentIcon(icon, 0.5f)
  }
}