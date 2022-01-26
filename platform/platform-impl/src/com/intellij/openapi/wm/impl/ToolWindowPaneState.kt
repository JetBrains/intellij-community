// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Pair
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.WindowInfo
import com.intellij.toolWindow.InternalDecoratorImpl
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap

internal class ToolWindowPaneState {
  private val idToSplitProportion = Object2FloatOpenHashMap<String>()
  var maximizedProportion: Pair<ToolWindow, Int>? = null

  var isStripesOverlaid = false

  fun getPreferredSplitProportion(id: String?, defaultValue: Float): Float {
    val f = idToSplitProportion.getFloat(id)
    return if (f == 0f) defaultValue else f
  }

  fun addSplitProportion(info: WindowInfo, component: InternalDecoratorImpl?, splitter: Splitter) {
    if (info.isSplit && component != null) {
      idToSplitProportion.put(component.toolWindow.id, splitter.proportion)
    }
  }

  fun isMaximized(window: ToolWindow): Boolean {
    return maximizedProportion != null && maximizedProportion!!.first === window
  }
}