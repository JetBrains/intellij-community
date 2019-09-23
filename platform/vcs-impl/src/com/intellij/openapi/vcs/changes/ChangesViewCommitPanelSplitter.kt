// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ui.OnePixelSplitter

private const val PROPORTION_KEY = "ChangesViewManager.COMMIT_SPLITTER_PROPORTION"

private class ChangesViewCommitPanelSplitter : OnePixelSplitter(true, PROPORTION_KEY, 1.0f) {
  private var isProportionSet = PropertiesComponent.getInstance().isValueSet(PROPORTION_KEY)

  override fun doLayout() {
    calculateInitialProportion()
    super.doLayout()
  }

  private fun calculateInitialProportion() {
    if (isProportionSet || height <= 0 || secondComponent == null) return

    isProportionSet = true
    proportion = 1.0f - (secondComponent.preferredSize.getHeight().toFloat() / height).coerceIn(0.05f, 0.95f)
  }

  override fun loadProportion() {
    if (isProportionSet) super.loadProportion()
  }

  override fun saveProportion() {
    if (isProportionSet) super.saveProportion()
  }
}
