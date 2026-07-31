// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.driver.sdk.ui.components.vcs

import com.intellij.driver.sdk.logicalPosition
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.actionButton
import com.intellij.driver.sdk.ui.xQuery

private const val DIFF_REQUEST_PROCESSOR_PANEL = $$"com.intellij.diff.impl.DiffRequestProcessor$MyPanel"
private const val DIFF_PANEL_BASE = "com.intellij.diff.tools.util.base.DiffPanelBase"
private const val ONE_SIDE_CONTENT_PANEL = "com.intellij.diff.tools.util.side.OnesideContentPanel"
private const val TWO_SIDE_CONTENT_PANEL = "com.intellij.diff.tools.util.side.TwosideContentPanel"
private const val THREE_SIDE_CONTENT_PANEL = "com.intellij.diff.tools.util.side.ThreesideContentPanel"
private const val DIFF_CONTENT_LAYOUT_PANEL = "com.intellij.diff.tools.util.side.DiffContentLayoutPanel"

class DiffProcessorUi(data: ComponentData) : UiComponent(data) {

  val previousDifferenceButton: ActionButtonUi = actionButton { byAccessibleName("Previous Difference") }
  val nextDifferenceButton: ActionButtonUi = actionButton { byAccessibleName("Next Difference") }
  val comparePreviousFileButton: ActionButtonUi = actionButton { byAccessibleName("Compare Previous File") }
  val compareNextFileButton: ActionButtonUi = actionButton { byAccessibleName("Compare Next File") }
  val collapseUnchangedFragmentsButton: ActionButtonUi = actionButton { byAccessibleName("Collapse Unchanged Fragments") }

  fun isEmpty(): Boolean = !x { byType(DIFF_PANEL_BASE) }.present()

  val isSideBySide: Boolean get() = x { byType(TWO_SIDE_CONTENT_PANEL) }.present()

  val isThreeSide: Boolean get() = x { byType(THREE_SIDE_CONTENT_PANEL) }.present()

  fun oneSide(): OneSideDiffContentPanelUi =
    x(OneSideDiffContentPanelUi::class.java) { byType(ONE_SIDE_CONTENT_PANEL) }.waitFound()

  @Suppress("unused")
  fun oneSide(action: OneSideDiffContentPanelUi.() -> Unit) {
    oneSide().action()
  }

  fun twoSide(): TwoSideDiffContentPanelUi =
    x(TwoSideDiffContentPanelUi::class.java) { byType(TWO_SIDE_CONTENT_PANEL) }.waitFound()

  fun twoSide(action: TwoSideDiffContentPanelUi.() -> Unit) {
    twoSide().action()
  }

  fun threeSide(): ThreeSideDiffContentPanelUi =
    x(ThreeSideDiffContentPanelUi::class.java) { byType(THREE_SIDE_CONTENT_PANEL) }.waitFound()

  @Suppress("unused")
  fun threeSide(action: ThreeSideDiffContentPanelUi.() -> Unit) {
    threeSide().action()
  }

  fun settingsButton(): ActionButtonUi = actionButton { byAccessibleName("Settings") }
}

fun DiffProcessorUi.changedContentEditor(): JEditorUiComponent = when {
  isThreeSide -> threeSide().rightContent().editor()
  isSideBySide -> twoSide().rightContent().editor()
  else -> oneSide().content().editor()
}

class OneSideDiffContentPanelUi(data: ComponentData) : UiComponent(data) {
  fun content(): UiComponent = x { byType(DIFF_CONTENT_LAYOUT_PANEL) }
}

class TwoSideDiffContentPanelUi(data: ComponentData) : UiComponent(data) {
  fun leftContent(): UiComponent = contentAt(1)
  fun rightContent(): UiComponent = contentAt(2)
}

@Suppress("unused")
class ThreeSideDiffContentPanelUi(data: ComponentData) : UiComponent(data) {
  fun leftContent(): UiComponent = contentAt(1)
  fun baseContent(): UiComponent = contentAt(2)
  fun rightContent(): UiComponent = contentAt(3)
}

private fun UiComponent.contentAt(ordinal: Int): UiComponent =
  x("(${xQuery { byType(DIFF_CONTENT_LAYOUT_PANEL) }})[$ordinal]")

fun Finder.diffProcessor(): DiffProcessorUi =
  x(DiffProcessorUi::class.java) { byType(DIFF_REQUEST_PROCESSOR_PANEL) }.waitFound()

fun Finder.diffProcessor(action: DiffProcessorUi.() -> Unit) {
  diffProcessor().action()
}

fun JEditorUiComponent.lastLineY(): Int {
  val lineCount = document.getLineCount()
  if (lineCount == 0) return 0
  return interact {
    logicalPositionToXY(driver.logicalPosition(lineCount - 1, 0)).getY().toInt()
  }
}