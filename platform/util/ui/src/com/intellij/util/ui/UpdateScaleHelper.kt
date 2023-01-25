// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.ui.scale.JBUIScale
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.JComponent

@Internal
class UpdateScaleHelper {
  private var savedUserScale = JBUIScale.scale(1f)

  fun saveScaleAndRunIfChanged(block: () -> Unit): Boolean {
    if (savedUserScale == JBUIScale.scale(1f)) return false

    try {
      block()
    }
    finally {
      savedUserScale = JBUIScale.scale(1f)
    }
    return true
  }

  fun saveScaleAndUpdateUIIfChanged(comp: JComponent): Boolean {
    return saveScaleAndRunIfChanged {
      updateUIForAll(comp)
    }
  }

  fun updateUIForAll(comp: JComponent) {
    UIUtil.uiTraverser(comp).forEach {
      (it as? JComponent)?.updateUI()
    }
  }
}
