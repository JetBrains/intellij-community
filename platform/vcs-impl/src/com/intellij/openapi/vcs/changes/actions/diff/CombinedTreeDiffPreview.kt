// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JTree

abstract class CombinedTreeDiffPreview(protected val tree: ChangesTree,
                                       targetComponent: JComponent,
                                       isOpenEditorDiffPreviewWithSingleClick: Boolean,
                                       needSetupOpenPreviewListeners: Boolean,
                                       parentDisposable: Disposable) :
  CombinedDiffPreview(tree.project, targetComponent, needSetupOpenPreviewListeners, parentDisposable) {

  constructor(tree: ChangesTree, parentDisposable: Disposable) :
    this(tree, tree, false, true, parentDisposable)

  init {
    if (needSetupOpenPreviewListeners) {
      installListeners(tree, isOpenEditorDiffPreviewWithSingleClick)
    }
    UIUtil.putClientProperty(tree, ExpandableItemsHandler.IGNORE_ITEM_SELECTION, true)
    tree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY) {
      updatePreview()
    }
  }
}

abstract class CombinedTreeDiffPreviewModel(protected val tree: ChangesTree, parentDisposable: Disposable) :
  CombinedDiffPreviewModel(tree.project, parentDisposable) {

  override fun selectChangeInSourceComponent(change: ChangeViewDiffRequestProcessor.Wrapper) {
    ChangesBrowserBase.selectObjectWithTag(tree, change.userObject, change.tag)
  }
}