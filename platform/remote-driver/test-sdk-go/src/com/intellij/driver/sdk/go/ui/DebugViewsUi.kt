package com.intellij.driver.sdk.go.ui

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.UIComponentsList
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.common.toolwindows.DebugToolWindowUi
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.xQuery

/** The "Mute Breakpoints" toggle of the toolbar of the Debug tool window. */
internal fun DebugToolWindowUi.muteBreakpointsButton(): ActionButtonUi =
  x(ActionButtonUi::class.java) { byAccessibleName(MUTE_BREAKPOINTS) }

/** The editor of the inline evaluate field above the Variables view. */
internal fun DebugToolWindowUi.evaluateField(): JEditorUiComponent =
  editor("//div[@class='$EVALUATE_COMBO_BOX']//div[@class='EditorComponentImpl']")

/** The editors of the console of the Debug tool window. Their documents hold the whole output. */
internal fun DebugToolWindowUi.consoleEditors(): UIComponentsList<JEditorUiComponent> =
  consoleView.xx(JEditorUiComponent::class.java) { byAccessibleName(CONSOLE_EDITOR) }

/** The "Add to Watches" button of the inline evaluate field. */
internal fun DebugToolWindowUi.addToWatchesButton(): UiComponent = x { byAccessibleName(ADD_TO_WATCHES) }

/** The editors of the expressions the debugger asks for, such as a breakpoint condition. A file editor is excluded. */
internal fun Finder.expressionEditors(): UIComponentsList<JEditorUiComponent> =
  xx(xQuery { and(byClass("EditorComponentImpl"), not(contains(byAccessibleName(FILE_EDITOR_NAME_PREFIX)))) },
     JEditorUiComponent::class.java)

/** The editor of the "Condition:" field of the breakpoint properties balloon. */
internal fun Finder.breakpointConditionEditor(): JEditorUiComponent =
  editor("//div[@class='ComboBox' and @accessiblename='$CONDITION_FIELD']//div[@class='EditorComponentImpl']")

/** The "Done" button of the breakpoint properties balloon. */
internal fun Finder.breakpointBalloonDoneButton(): UiComponent = x { and(byClass("JButton"), byAccessibleName(DONE_BUTTON)) }

/** A popup group of a context menu, for example "View as". A plain click opens its submenu. */
internal fun Finder.contextMenuGroup(name: String): UiComponent = x { and(byClass("ActionMenu"), byAccessibleName(name)) }

/** The items of an open context menu, optionally filtered by [name]. */
internal fun Finder.contextMenuItems(name: String? = null): UIComponentsList<UiComponent> =
  xx { if (name == null) byClass("ActionMenuItem") else and(byClass("ActionMenuItem"), byAccessibleName(name)) }

private const val MUTE_BREAKPOINTS = "Mute Breakpoints"
private const val EVALUATE_COMBO_BOX = "XDebuggerEmbeddedComboBox"
private const val CONSOLE_EDITOR = "Editor"
private const val ADD_TO_WATCHES = "Add to Watches"
private const val FILE_EDITOR_NAME_PREFIX = "Editor for"
private const val CONDITION_FIELD = "Condition:"
private const val DONE_BUTTON = "Done"
