// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.EditorTabPreview
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import javax.swing.JComponent

@Deprecated("Use ChangesTreeEditorDiffPreview")
@ScheduledForRemoval
abstract class SimpleTreeEditorDiffPreview(
  protected val changeViewProcessor: ChangeViewDiffRequestProcessor,
  tree: ChangesTree,
  targetComponent: JComponent,
  isOpenEditorDiffPreviewWithSingleClick: Boolean
) : EditorTabPreview(changeViewProcessor) {

  constructor(diffProcessor: ChangeViewDiffRequestProcessor, tree: ChangesTree) : this(diffProcessor, tree, tree, false)

  init {
    escapeHandler = Runnable {
      closePreview()
      returnFocusToTree()
    }

    installListeners(tree, isOpenEditorDiffPreviewWithSingleClick)
    installNextDiffActionOn(targetComponent)
    UIUtil.putClientProperty(tree, ExpandableItemsHandler.IGNORE_ITEM_SELECTION, true)
  }

  open fun returnFocusToTree() = Unit

  override fun updateDiffAction(event: AnActionEvent) {
    event.presentation.isVisible = event.isFromActionToolbar || event.presentation.isEnabled
  }

  override fun hasContent(): Boolean {
    return changeViewProcessor.currentChange != null
  }
}
