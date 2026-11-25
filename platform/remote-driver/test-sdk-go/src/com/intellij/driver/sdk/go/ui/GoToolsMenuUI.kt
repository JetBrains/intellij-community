package com.intellij.driver.sdk.go.ui

import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.UIComponentsList
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.codeEditorForFile

/** Scrolls to [anchor] and opens the context menu of the editor. */
internal fun Finder.rightClickEditorOn(anchor: String) {
  codeEditor().scrollAndRightClickOn(anchor)
}

/** Opens the context menu of the editor of [fileName] on [anchor], a fragment of the text of that file. */
fun Finder.rightClickEditorOn(fileName: String, anchor: String) {
  codeEditorForFile(fileName).scrollAndRightClickOn(anchor)
}

private fun JEditorUiComponent.scrollAndRightClickOn(anchor: String) {
  val offset = text.indexOf(anchor)
  check(offset >= 0) { "The editor has no '$anchor'" }
  moveCaretToOffset(offset + anchor.length / 2)
  scrollToCaret()
  clickOn(anchor, RemoteMouseButton.RIGHT)
}

/** The `Go Tools` submenu of the context menu: a popup group is an `ActionMenu`, and a plain click opens it. */
internal fun Finder.goToolsMenu(): UiComponent =
  x { and(byClass("ActionMenu"), byAccessibleName(GO_TOOLS_MENU)) }

/**
 * The items of the `Go Tools` submenu that carry [nameFragment].
 *
 * An item appears twice in the hierarchy — once under the `ActionMenu` that owns it, once under the shown window —
 * with the same bounds, so the items are matched as a list: `x` throws on more than one match.
 */
internal fun Finder.goToolsMenuItems(nameFragment: String): UIComponentsList<UiComponent> =
  xx { and(byClass("ActionMenuItem"), contains(byAccessibleName(nameFragment))) }

private const val GO_TOOLS_MENU = "Go Tools"
