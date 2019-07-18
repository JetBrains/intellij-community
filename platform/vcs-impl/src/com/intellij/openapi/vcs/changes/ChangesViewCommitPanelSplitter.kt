// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.ide.util.PropertiesComponent
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.update.UiNotifyConnector
import javax.swing.JComponent

private const val CHANGES_VIEW_COMMIT_SPLITTER_PROPORTION = "ChangesViewManager.COMMIT_SPLITTER_PROPORTION"

private class ChangesViewCommitPanelSplitter : OnePixelSplitter(true, CHANGES_VIEW_COMMIT_SPLITTER_PROPORTION, 1.0f) {
  private var isDefaultProportionSet = false

  override fun setSecondComponent(component: JComponent?) {
    if (component != null && !isDefaultProportionSet) {
      UiNotifyConnector.doWhenFirstShown(this) {
        isDefaultProportionSet = true
        proportion = 1.0f - (component.preferredSize.getHeight().toFloat() / height).coerceIn(0.05f, 0.95f)
      }
    }
    super.setSecondComponent(component)
  }

  override fun loadProportion() {
    val key = splitterProportionKey
    isDefaultProportionSet = key != null && PropertiesComponent.getInstance().isValueSet(key)

    if (isDefaultProportionSet) super.loadProportion()
  }

  override fun saveProportion() {
    if (isDefaultProportionSet) super.saveProportion()
  }
}
