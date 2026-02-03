// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.JComponent
import kotlin.math.abs

@Internal
open class UpdateScaleHelper(val forceInitialRun: Boolean = false, val currentValue: (() -> Float?) = { JBFont.labelFontSize2D() }) {
  private var savedValue: Float? = currentValue()
  private var initialRunPerformed = false

  fun saveScaleAndRunIfChanged(runnable: Runnable): Boolean =
    saveScaleAndRunIfChanged { runnable.run() }

  fun saveScaleAndRunIfChanged(block: () -> Unit): Boolean {
    val currentValue = currentValue()
    val savedValue = savedValue

    if ((!forceInitialRun || initialRunPerformed)
        && currentValue != null &&  savedValue != null
        && savedValue.equalsWithinEpsilonTo(currentValue)) return false

    try {
      block()
    }
    finally {
      initialRunPerformed = true
      this.savedValue = currentValue
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

  private fun Float.equalsWithinEpsilonTo(other: Float): Boolean = abs(this - other) < 0.001
}
