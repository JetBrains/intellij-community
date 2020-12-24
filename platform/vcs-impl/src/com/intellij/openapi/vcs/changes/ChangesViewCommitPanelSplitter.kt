// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener
import com.intellij.openapi.vcs.changes.ui.isCommitToolWindowShown
import com.intellij.ui.OnePixelSplitter

private const val VERTICAL_PROPORTION_KEY = "ChangesViewManager.COMMIT_SPLITTER_PROPORTION"
private const val HORIZONTAL_PROPORTION_KEY = "ChangesViewManager.COMMIT_SPLITTER_PROPORTION.HORIZONTAL"
private const val DEFAULT_VERTICAL_PROPORTION = 1.0f
private const val DEFAULT_HORIZONTAL_PROPORTION = 0.4f

private val propertiesComponent get() = PropertiesComponent.getInstance()

private const val COMMIT_TOOL_WINDOW_PROPORTION_KEY = "CommitToolWindow.COMMIT_SPLITTER_PROPORTION"
private const val COMMIT_TOOL_WINDOW_DEFAULT_PROPORTION = 0.6f

private fun getVerticalProportionKey(project: Project) =
  if (project.isCommitToolWindowShown) COMMIT_TOOL_WINDOW_PROPORTION_KEY else VERTICAL_PROPORTION_KEY

private fun getDefaultVerticalProportion(project: Project) =
  if (project.isCommitToolWindowShown) COMMIT_TOOL_WINDOW_DEFAULT_PROPORTION else DEFAULT_VERTICAL_PROPORTION

private class ChangesViewCommitPanelSplitter(private val project: Project) :
  OnePixelSplitter(true, "", getDefaultVerticalProportion(project)),
  ChangesViewContentManagerListener,
  Disposable {

  private var isVerticalProportionSet = isVerticalProportionSet()
  private var previousHeight = 0
  private var verticalSecondHeight = 0

  init {
    addPropertyChangeListener(PROP_ORIENTATION) { loadProportion() }
    project.messageBus.connect(this).subscribe(ChangesViewContentManagerListener.TOPIC, this)
  }

  private fun isVerticalProportionSet() = project.isCommitToolWindowShown || propertiesComponent.isValueSet(getVerticalProportionKey(project))

  override fun toolWindowMappingChanged() {
    isVerticalProportionSet = isVerticalProportionSet()
    revalidate()
    repaint()
  }

  override fun doLayout() {
    if (project.isCommitToolWindowShown) {
      super.doLayout()
    }
    else {
      calculateInitialVerticalProportion()
      doLayoutRetainingSecondHeight()
    }
  }

  private fun doLayoutRetainingSecondHeight() {
    if (isVerticalProportionSet && canCalculateVerticalProportion() && isSplitterHeightChanged()) {
      if (isVertical && secondComponent.height > 0) proportion = getProportionForSecondHeight(secondComponent.height)
      else if (!isVertical && verticalSecondHeight > 0) saveVerticalProportion(getProportionForSecondHeight(verticalSecondHeight))
    }
    super.doLayout()
    previousHeight = height
    if (isVerticalProportionSet && isVertical && canCalculateVerticalProportion()) verticalSecondHeight = secondComponent.height
  }

  private fun calculateInitialVerticalProportion() {
    if (isVerticalProportionSet || !isVertical || !canCalculateVerticalProportion()) return

    isVerticalProportionSet = true
    proportion = getProportionForSecondHeight(secondComponent.preferredSize.height)
  }

  private fun getProportionForSecondHeight(secondHeight: Int): Float =
    getProportionForSecondSize(secondHeight, height).coerceIn(0.05f, 0.95f)

  private fun canCalculateVerticalProportion(): Boolean = secondComponent != null && height > dividerWidth
  private fun isSplitterHeightChanged(): Boolean = previousHeight != height

  override fun loadProportion() {
    if (!isVertical) {
      proportion = propertiesComponent.getFloat(HORIZONTAL_PROPORTION_KEY, DEFAULT_HORIZONTAL_PROPORTION)
    }
    else if (isVerticalProportionSet) {
      proportion = propertiesComponent.getFloat(getVerticalProportionKey(project), getDefaultVerticalProportion(project))
    }
  }

  override fun saveProportion() {
    if (!isVertical) {
      propertiesComponent.setValue(HORIZONTAL_PROPORTION_KEY, proportion, DEFAULT_HORIZONTAL_PROPORTION)
    }
    else if (isVerticalProportionSet) {
      saveVerticalProportion(proportion)
    }
  }

  private fun saveVerticalProportion(value: Float) =
    propertiesComponent.setValue(getVerticalProportionKey(project), value, getDefaultVerticalProportion(project))
}