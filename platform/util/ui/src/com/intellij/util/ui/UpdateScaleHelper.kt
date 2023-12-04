// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.ui.scale.JBUIScale
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.JComponent

@Internal
open class UpdateScaleHelper(val forceInitialRun: Boolean = false, val currentScale: (() -> Float) = { JBUIScale.scale(1f) }) {
  private var savedScale: Float = currentScale()
  private var initialRunPerformed = false

  fun saveScaleAndRunIfChanged(runnable: Runnable): Boolean =
    saveScaleAndRunIfChanged { runnable.run() }

  fun saveScaleAndRunIfChanged(block: () -> Unit): Boolean {
    if ((!forceInitialRun || initialRunPerformed)
        && savedScale == currentScale()) return false

    try {
      block()
    }
    finally {
      initialRunPerformed = true
      savedScale = currentScale()
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
