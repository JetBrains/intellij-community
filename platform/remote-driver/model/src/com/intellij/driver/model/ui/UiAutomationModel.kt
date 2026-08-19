// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.driver.model.ui

import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.model.transport.PassByValue

enum class UiRole : PassByValue {
  ALERT,
  BUTTON,
  CHECK_BOX,
  COMBO_BOX,
  DIALOG,
  FRAME,
  ICON,
  LABEL,
  LIST,
  LIST_ITEM,
  MENU,
  MENU_BAR,
  MENU_ITEM,
  PAGE_TAB,
  PAGE_TAB_LIST,
  PANEL,
  PASSWORD_TEXT,
  POPUP_MENU,
  PROGRESS_BAR,
  RADIO_BUTTON,
  SCROLL_BAR,
  SCROLL_PANE,
  SEPARATOR,
  SLIDER,
  SPIN_BOX,
  SPLIT_PANE,
  STATUS_BAR,
  TABLE,
  TEXT,
  TOGGLE_BUTTON,
  TOOL_BAR,
  TOOL_TIP,
  TREE,
  WINDOW,
  UNKNOWN,
}

enum class UiState : PassByValue {
  ACTIVE,
  ARMED,
  BUSY,
  CHECKED,
  COLLAPSED,
  EDITABLE,
  ENABLED,
  EXPANDED,
  FOCUSABLE,
  FOCUSED,
  HORIZONTAL,
  INDETERMINATE,
  MODAL,
  MULTI_LINE,
  MULTISELECTABLE,
  OPAQUE,
  PRESSED,
  RESIZABLE,
  SELECTABLE,
  SELECTED,
  SHOWING,
  SINGLE_LINE,
  TRANSIENT,
  TRUNCATED,
  VERTICAL,
  VISIBLE,
}

data class UiBounds(
  val x: Int,
  val y: Int,
  val width: Int,
  val height: Int,
) : PassByValue

data class UiValue(
  val current: Double?,
  val minimum: Double?,
  val maximum: Double?,
) : PassByValue

data class UiNode(
  val id: Long,
  val className: String,
  val javaClassName: String,
  val role: UiRole,
  val name: String?,
  val description: String?,
  val texts: List<String>,
  val states: Set<UiState>,
  val actions: List<String>,
  val value: UiValue?,
  val bounds: UiBounds?,
  val attributes: Map<String, String>,
  val children: List<UiNode>,
  val text: String? = null,
) : PassByValue

data class UiSnapshot(
  val revision: Long,
  val root: UiNode,
) : PassByValue

sealed interface UiLocator : PassByValue {
  data class AccessibleName(val value: String, val exact: Boolean = true) : UiLocator
  data class Role(val role: UiRole, val name: String? = null, val exactName: Boolean = true) : UiLocator
  data class Text(val value: String, val exact: Boolean = false) : UiLocator
  data class ClassName(val value: String) : UiLocator
  data class Within(val ancestor: UiLocator, val descendant: UiLocator) : UiLocator
  data class Nth(val locator: UiLocator, val index: Int) : UiLocator
  data class Xpath(val value: String) : UiLocator
}

data class UiClickRequest(
  val locator: UiLocator,
  val button: RemoteMouseButton = RemoteMouseButton.LEFT,
  val clickCount: Int = 1,
  val timeoutMs: Long = 15_000,
) : PassByValue

data class UiFillRequest(
  val locator: UiLocator,
  val text: String,
  val timeoutMs: Long = 15_000,
) : PassByValue

data class UiDragRequest(
  val from: UiLocator,
  val to: UiLocator,
  val timeoutMs: Long = 15_000,
) : PassByValue

data class UiActionResult(
  val target: UiNode,
  val beforeRevision: Long,
  val afterRevision: Long,
) : PassByValue
