// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Pair
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.WindowInfo
import gnu.trove.TObjectFloatHashMap

internal class ToolWindowPaneState {
  private val idToSplitProportion = TObjectFloatHashMap<String>()
  var maximizedProportion: Pair<ToolWindow, Int>? = null

  var isStripesOverlaid = false

  fun getPreferredSplitProportion(id: String?, defaultValue: Float): Float {
    val f = idToSplitProportion.get(id)
    return if (f == 0f) defaultValue else f
  }

  fun addSplitProportion(info: WindowInfo, component: InternalDecorator?, splitter: Splitter) {
    if (info.isSplit && component != null) {
      idToSplitProportion.put(component.toolWindow.id, splitter.proportion)
    }
  }

  fun isMaximized(window: ToolWindow): Boolean {
    return maximizedProportion != null && maximizedProportion!!.first === window
  }
}