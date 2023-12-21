// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.JComponent
import javax.swing.UIManager

@Internal
open class UpdateScaleHelper(val forceInitialRun: Boolean = false, val currentValue: (() -> Float) = {
  UIManager.getFont("Label.font").size2D
}) {
  private var savedValue: Float = currentValue()
  private var initialRunPerformed = false

  fun saveScaleAndRunIfChanged(runnable: Runnable): Boolean =
    saveScaleAndRunIfChanged { runnable.run() }

  fun saveScaleAndRunIfChanged(block: () -> Unit): Boolean {
    if ((!forceInitialRun || initialRunPerformed)
        && savedValue == currentValue()) return false

    try {
      block()
    }
    finally {
      initialRunPerformed = true
      savedValue = currentValue()
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
