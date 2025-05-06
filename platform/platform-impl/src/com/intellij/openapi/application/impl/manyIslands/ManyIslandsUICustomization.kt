// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.manyIslands

import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.application.impl.ToolWindowUIDecorator
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.toolWindow.xNext.island.XNextIslandHolder
import com.intellij.ui.ClientProperty
import com.intellij.ui.JBColor
import com.intellij.ui.tabs.impl.TabPainterAdapter
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JComponent

@ApiStatus.Internal
internal class ManyIslandsUICustomization : InternalUICustomization() {
  private val isManyIslandsEnabled: Boolean = Registry.`is`("idea.many.islands.enabled", false)

  override val isDefaultCustomization: Boolean = !isManyIslandsEnabled

  private val toolWindowDecorator = object : ToolWindowUIDecorator() {
    override fun decorateAndReturnHolder(divider: JComponent, child: JComponent): JComponent? {
      return XNextIslandHolder().apply {
        layout = BorderLayout()
        add(divider, BorderLayout.NORTH)
        add(child, BorderLayout.CENTER)

        ManyIslandsRoundedBorder.createToolWindowBorder(this, child)
        child.putClientProperty(IdeBackgroundUtil.NO_BACKGROUND, true)
      }
    }
  }

  private val tabPainterAdapter = ManyIslandsTabPainterAdapter()

  override val toolWindowUIDecorator: ToolWindowUIDecorator
    get() {
      if (isManyIslandsEnabled) {
        return toolWindowDecorator
      }
      return super.toolWindowUIDecorator
    }

  override fun installEditorBackground(component: JComponent) {
    if (isManyIslandsEnabled) {
      ClientProperty.putRecursive(component, IdeBackgroundUtil.NO_BACKGROUND, true)
    }
  }

  override fun configureEditorsSplitters(component: EditorsSplitters) {
    if (isManyIslandsEnabled) {
      ManyIslandsRoundedBorder.createEditorBorder(component)
    }
  }

  override val editorTabPainterAdapter: TabPainterAdapter
    get() {
      if (isManyIslandsEnabled) {
        return tabPainterAdapter
      }
      return super.editorTabPainterAdapter
    }

  override fun getCustomMainBackgroundColor(): Color? {
    if (isManyIslandsEnabled) {
      return JBColor.namedColor("MainWindow.background", JBColor.PanelBackground)
    }
    return super.getCustomMainBackgroundColor()
  }
}