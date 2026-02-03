// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.impl.IslandsPainterProvider
import com.intellij.ui.tabs.impl.TabPainterAdapter
import java.awt.Insets

internal class IslandsInternalPainterProvider : IslandsPainterProvider() {
  override fun createCommonTabPainter(): TabPainterAdapter? {
    return InternalUICustomization.getInstance()?.commonTabPainterAdapter
  }

  override fun useMacScrollBar(): Boolean {
    return InternalUICustomization.getInstance()?.isMacScrollBar == true
  }

  override fun isTabOccupiesWholeHeight(): Boolean {
    val customization = InternalUICustomization.getInstance()
    return customization?.isTabOccupiesWholeHeight ?: super.isTabOccupiesWholeHeight()
  }

  override fun getSingleRowTabInsets(tabsPosition: JBTabsPosition): Insets? {
    val customization = InternalUICustomization.getInstance()
    return if (customization == null) super.getSingleRowTabInsets(tabsPosition) else customization.getSingleRowTabInsets(tabsPosition)
  }
}