// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ui.OnePixelSplitter

private const val VERTICAL_PROPORTION_KEY = "ChangesViewManager.COMMIT_SPLITTER_PROPORTION"
private const val HORIZONTAL_PROPORTION_KEY = "ChangesViewManager.COMMIT_SPLITTER_PROPORTION.HORIZONTAL"
private const val DEFAULT_VERTICAL_PROPORTION = 1.0f
private const val DEFAULT_HORIZONTAL_PROPORTION = 0.4f

private val propertiesComponent get() = PropertiesComponent.getInstance()

private class ChangesViewCommitPanelSplitter : OnePixelSplitter(true, "", DEFAULT_VERTICAL_PROPORTION) {
  private var isVerticalProportionSet = propertiesComponent.isValueSet(VERTICAL_PROPORTION_KEY)

  init {
    addPropertyChangeListener(PROP_ORIENTATION) { loadProportion() }
  }

  override fun doLayout() {
    calculateInitialVerticalProportion()
    super.doLayout()
  }

  private fun calculateInitialVerticalProportion() {
    if (!isVertical || isVerticalProportionSet || height <= 0 || secondComponent == null) return

    isVerticalProportionSet = true
    proportion = 1.0f - (secondComponent.preferredSize.getHeight().toFloat() / height).coerceIn(0.05f, 0.95f)
  }

  override fun loadProportion() {
    if (!isVertical) {
      proportion = propertiesComponent.getFloat(HORIZONTAL_PROPORTION_KEY, DEFAULT_HORIZONTAL_PROPORTION)
    }
    else if (isVerticalProportionSet) {
      proportion = propertiesComponent.getFloat(VERTICAL_PROPORTION_KEY, DEFAULT_VERTICAL_PROPORTION)
    }
  }

  override fun saveProportion() {
    if (!isVertical) {
      propertiesComponent.setValue(HORIZONTAL_PROPORTION_KEY, proportion, DEFAULT_HORIZONTAL_PROPORTION)
    }
    else if (isVerticalProportionSet) {
      propertiesComponent.setValue(VERTICAL_PROPORTION_KEY, proportion, DEFAULT_VERTICAL_PROPORTION)
    }
  }
}