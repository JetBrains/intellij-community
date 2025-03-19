// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.EditSourceOnDoubleClickHandler
import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

@ApiStatus.Internal
abstract class ChangesTreeEditorDiffPreview<T : ChangesTree>(protected val tree: T) {
  init {
    tree.setEnterKeyHandler { handleEnterKey(it) }
    tree.setDoubleClickHandler { handleDoubleClick(it) }
  }

  open fun handleDoubleClick(e: MouseEvent): Boolean {
    if (!isPreviewOnDoubleClick()) return false
    if (EditSourceOnDoubleClickHandler.isToggleEvent(tree, e)) return false
    return performDiffAction()
  }

  open fun handleEnterKey(e: KeyEvent): Boolean {
    if (!isPreviewOnEnter()) return false
    return performDiffAction()
  }

  protected open fun isPreviewOnDoubleClick(): Boolean = true

  protected open fun isOpenPreviewWithSingleClickEnabled(): Boolean = false

  protected open fun isPreviewOnEnter(): Boolean = true

  protected open fun isOpenPreviewWithSingleClick(): Boolean {
    if (!isOpenPreviewWithSingleClickEnabled()) return false
    val project = tree.project
    if (tree != IdeFocusManager.getInstance(project).focusOwner) return false
    return true
  }


  protected open fun handleSingleClick() {
    if (!isOpenPreviewWithSingleClick()) return
    performDiffAction()
  }

  abstract fun performDiffAction(): Boolean
}

@ApiStatus.Internal
val EDITOR_TAB_DIFF_PREVIEW: DataKey<ChangesTreeEditorDiffPreview<*>> = DataKey.create("EditorTabDiffPreview")