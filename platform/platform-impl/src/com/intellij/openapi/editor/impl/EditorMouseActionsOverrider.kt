// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Lets extensions override which action IDs [EditorImpl] consults when resolving mouse-shortcut-driven
 * multi-caret gestures (rectangular selection, add/remove caret) for a given [Editor], instead of default actions.
 * Implementations are expected to check whatever makes an editor "theirs" and return `null` from every method
 * for editors they don't own.
 *
 * Each method may return `null` (the default) to keep the platform's own action ID for that particular
 * gesture - an implementation only needs to override the ones it actually cares about.
 * [EP_NAME]'s extensions are consulted in registration order; the first non-null result wins.
 */
@ApiStatus.Internal
interface EditorMouseActionsOverrider {
  /** @see com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_CREATE_RECTANGULAR_SELECTION */
  fun getCreateRectangularSelectionActionId(editor: Editor): String? = null

  /** @see com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_CREATE_RECTANGULAR_SELECTION_ON_MOUSE_DRAG */
  fun getCreateRectangularSelectionOnMouseDragActionId(editor: Editor): String? = null

  /** @see com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ADD_RECTANGULAR_SELECTION_ON_MOUSE_DRAG */
  fun getAddRectangularSelectionOnMouseDragActionId(editor: Editor): String? = null

  /** @see com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ADD_OR_REMOVE_CARET */
  fun getAddOrRemoveCaretActionId(editor: Editor): String? = null

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<EditorMouseActionsOverrider> = ExtensionPointName("com.intellij.editorMouseActionsOverrider")
  }
}
