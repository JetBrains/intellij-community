// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.ui.tabs.impl.IslandsPainterProvider
import com.intellij.ui.tabs.impl.TabPainterAdapter

internal class IslandsInternalPainterProvider : IslandsPainterProvider() {
  override fun createCommonTabPainter(): TabPainterAdapter? {
    return InternalUICustomization.getInstance()?.commonTabPainterAdapter
  }

  override fun useMacScrollBar(): Boolean {
    return InternalUICustomization.getInstance()?.isMacScrollBar == true
  }
}