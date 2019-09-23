// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.update.UiNotifyConnector.doWhenFirstShown
import javax.swing.JComponent

private const val PROPORTION_KEY = "ChangesViewManager.COMMIT_SPLITTER_PROPORTION"

private class ChangesViewCommitPanelSplitter : OnePixelSplitter(true, PROPORTION_KEY, 1.0f) {
  private var isProportionSet = PropertiesComponent.getInstance().isValueSet(PROPORTION_KEY)

  override fun setSecondComponent(component: JComponent?) {
    super.setSecondComponent(component)

    if (component != null && !isProportionSet) {
      calculateProportion()
    }
  }

  private fun calculateProportion() =
    doWhenFirstShown(this) {
      val component = secondComponent ?: return@doWhenFirstShown
      if (isProportionSet) return@doWhenFirstShown

      isProportionSet = true
      proportion = 1.0f - (component.preferredSize.getHeight().toFloat() / height).coerceIn(0.05f, 0.95f)
    }

  override fun loadProportion() {
    if (isProportionSet) super.loadProportion()
  }

  override fun saveProportion() {
    if (isProportionSet) super.saveProportion()
  }
}
