// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Pair
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.WindowInfo
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap

internal class ToolWindowPaneState {
  private val idToSplitProportion = Object2FloatOpenHashMap<String>()
  var maximizedProportion: Pair<ToolWindow, Int>? = null

  var isStripesOverlaid: Boolean = false

  fun getPreferredSplitProportion(id: String?, defaultValue: Float): Float {
    val f = idToSplitProportion.getFloat(id)
    return if (f == 0f) {
      ToolWindowPane.log().debug { "Using default split proportion of $defaultValue for $id because there's no saved one" }
      defaultValue
    }
    else {
      ToolWindowPane.log().debug { "Using the saved split proportion of $f for $id" }
      f
    }
  }

  fun addSplitProportion(info: WindowInfo, component: InternalDecoratorImpl?, splitter: Splitter) {
    if (info.isSplit && component != null) {
      ToolWindowPane.log().debug { "Saving the split proportion of ${splitter.proportion} for ${component.toolWindow.id}" }
      idToSplitProportion.put(component.toolWindow.id, splitter.proportion)
    }
  }

  fun isMaximized(window: ToolWindow): Boolean {
    return maximizedProportion != null && maximizedProportion!!.first === window
  }
}