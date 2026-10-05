// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.driver.sdk.ui.components.vcs

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.logicalPosition
import com.intellij.driver.sdk.ui.DEFAULT_FIND_TIMEOUT
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.WindowUiComponent
import com.intellij.driver.sdk.ui.components.elements.actionButton
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitFor
import org.intellij.lang.annotations.Language

private const val DIFF_REQUEST_PROCESSOR_PANEL = $$"com.intellij.diff.impl.DiffRequestProcessor$MyPanel"
private const val DIFF_PANEL_BASE = "com.intellij.diff.tools.util.base.DiffPanelBase"
private const val ONE_SIDE_CONTENT_PANEL = "com.intellij.diff.tools.util.side.OnesideContentPanel"
private const val TWO_SIDE_CONTENT_PANEL = "com.intellij.diff.tools.util.side.TwosideContentPanel"
private const val THREE_SIDE_CONTENT_PANEL = "com.intellij.diff.tools.util.side.ThreesideContentPanel"
private const val DIFF_CONTENT_LAYOUT_PANEL = "com.intellij.diff.tools.util.side.DiffContentLayoutPanel"
private const val EDITOR_COMPONENT = "com.intellij.openapi.editor.impl.EditorComponentImpl"
private const val FRAME_WRAPPER_FRAME = "com.intellij.openapi.ui.MyJFrame"

class DiffProcessorUi(private val data: ComponentData) : UiComponent(data) {

  val previousDifferenceButton: ActionButtonUi = actionButton { byAccessibleName("Previous Difference") }
  val nextDifferenceButton: ActionButtonUi = actionButton { byAccessibleName("Next Difference") }
  val comparePreviousFileButton: ActionButtonUi = actionButton { byAccessibleName("Compare Previous File") }
  val compareNextFileButton: ActionButtonUi = actionButton { byAccessibleName("Compare Next File") }
  val collapseUnchangedFragmentsButton: ActionButtonUi = actionButton { byAccessibleName("Collapse Unchanged Fragments") }

  fun isEmpty(): Boolean = !data.descendant(UiComponent::class.java, DIFF_PANEL_BASE).present()

  val isOneSide: Boolean get() = data.descendant(UiComponent::class.java, ONE_SIDE_CONTENT_PANEL).present()

  val isSideBySide: Boolean get() = data.descendant(UiComponent::class.java, TWO_SIDE_CONTENT_PANEL).present()

  val isThreeSide: Boolean get() = data.descendant(UiComponent::class.java, THREE_SIDE_CONTENT_PANEL).present()

  fun oneSide(): OneSideDiffContentPanelUi =
    data.descendant(OneSideDiffContentPanelUi::class.java, ONE_SIDE_CONTENT_PANEL).waitFound()

  @Suppress("unused")
  fun oneSide(action: OneSideDiffContentPanelUi.() -> Unit) {
    oneSide().action()
  }

  fun twoSide(): TwoSideDiffContentPanelUi =
    data.descendant(TwoSideDiffContentPanelUi::class.java, TWO_SIDE_CONTENT_PANEL).waitFound()

  fun twoSide(action: TwoSideDiffContentPanelUi.() -> Unit) {
    twoSide().action()
  }

  fun threeSide(): ThreeSideDiffContentPanelUi =
    data.descendant(ThreeSideDiffContentPanelUi::class.java, THREE_SIDE_CONTENT_PANEL).waitFound()

  @Suppress("unused")
  fun threeSide(action: ThreeSideDiffContentPanelUi.() -> Unit) {
    threeSide().action()
  }

  fun settingsButton(): ActionButtonUi = actionButton { byAccessibleName("Settings") }
}

/**
 * Returns the editor of the changed side of the diff.
 *
 * The diff viewer can show some time after the [DiffProcessorUi]. For example, in the Split mode the backend sends the viewer later.
 * Thus, the function waits until one of the viewer panels shows, and then selects the panel.
 */
fun DiffProcessorUi.changedContentEditor(): JEditorUiComponent {
  waitFor("Diff viewer is shown", DEFAULT_FIND_TIMEOUT) { isThreeSide || isSideBySide || isOneSide }
  return when {
    isThreeSide -> threeSide().rightEditor()
    isSideBySide -> twoSide().rightEditor()
    else -> oneSide().contentEditor()
  }
}

class OneSideDiffContentPanelUi(private val data: ComponentData) : UiComponent(data) {
  fun content(): UiComponent = data.descendant(UiComponent::class.java, DIFF_CONTENT_LAYOUT_PANEL)
  fun contentEditor(): JEditorUiComponent = editorAt(1)
}

class TwoSideDiffContentPanelUi(private val data: ComponentData) : UiComponent(data) {
  fun leftContent(): UiComponent = data.contentAt(1)
  fun rightContent(): UiComponent = data.contentAt(2)
  fun leftEditor(): JEditorUiComponent = editorAt(1)
  fun rightEditor(): JEditorUiComponent = editorAt(2)
}

@Suppress("unused")
class ThreeSideDiffContentPanelUi(private val data: ComponentData) : UiComponent(data) {
  fun leftContent(): UiComponent = data.contentAt(1)
  fun baseContent(): UiComponent = data.contentAt(2)
  fun rightContent(): UiComponent = data.contentAt(3)
  fun leftEditor(): JEditorUiComponent = editorAt(1)
  fun baseEditor(): JEditorUiComponent = editorAt(2)
  fun rightEditor(): JEditorUiComponent = editorAt(3)
}

private fun ComponentData.contentAt(ordinal: Int): UiComponent =
  UiComponent(copy(xpath = "(($xpath)[1]${xQuery { byType(DIFF_CONTENT_LAYOUT_PANEL) }})[$ordinal]", foundComponent = null, readableName = null))

/**
 * Finds the editor of one side of the diff. The [ordinal] counts the sides from the left, starting from 1.
 *
 * The search runs inside this panel. In the Split mode, an editor that a search from the parent context finds can give the editor of another side.
 */
private fun UiComponent.editorAt(ordinal: Int): JEditorUiComponent =
  x("(${xQuery { byType(EDITOR_COMPONENT) }})[$ordinal]", JEditorUiComponent::class.java)

/**
 * Finds a descendant of the [panelClass] class inside the first component that matches this data.
 *
 * The search starts from the parent search context, with a joined XPath. In the Split mode, a diff panel is a BeControl.
 * A search inside a BeControl does not see the backend classes of the descendants, but a search from the parent context sees them.
 */
private fun <T : UiComponent> ComponentData.descendant(componentType: Class<T>, @Language("jvm-class-name") panelClass: String): T =
  componentType.getConstructor(ComponentData::class.java)
    .newInstance(copy(xpath = "($xpath)[1]${xQuery { byType(panelClass) }}", foundComponent = null, readableName = null))

fun Finder.diffProcessor(): DiffProcessorUi =
  x(DiffProcessorUi::class.java) { byType(DIFF_REQUEST_PROCESSOR_PANEL) }.waitFound()

fun Finder.diffProcessor(action: DiffProcessorUi.() -> Unit) {
  diffProcessor().action()
}

/**
 * The separate window that holds a diff. The IDE opens it when the advanced setting `show.diff.as.editor.tab` is off.
 */
fun Finder.diffWindow(): WindowUiComponent =
  x(xQuery { byJavaClass(FRAME_WRAPPER_FRAME) } +
    "[.${xQuery { byType(DIFF_REQUEST_PROCESSOR_PANEL) }}]", WindowUiComponent::class.java, "diff window").waitFound()

fun Driver.diffWindow(): WindowUiComponent = ui.diffWindow()

fun JEditorUiComponent.lastLineY(): Int {
  val lineCount = document.getLineCount()
  if (lineCount == 0) return 0
  return interact {
    logicalPositionToXY(driver.logicalPosition(lineCount - 1, 0)).getY().toInt()
  }
}