// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.Wrapper
import com.intellij.openapi.vcs.changes.savedPatches.SavedPatchesUi.Companion.SAVED_PATCHES_UI_PLACE
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.util.containers.JBIterable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class SavedPatchesDiffProcessor(tree: ChangesTree, private val isInEditor: Boolean, isShowDiffWithLocal: () -> Boolean)
  : TreeHandlerDiffRequestProcessor(SAVED_PATCHES_UI_PLACE, tree, SavedPatchesDiffPreviewHandler(isShowDiffWithLocal)) {
  init {
    TreeHandlerChangesTreeTracker(tree, this, handler, !isInEditor).track()
  }

  override fun shouldAddToolbarBottomBorder(toolbarComponents: FrameDiffTool.ToolbarComponents): Boolean {
    return !isInEditor || super.shouldAddToolbarBottomBorder(toolbarComponents)
  }
}

@ApiStatus.Internal
class SavedPatchesDiffPreviewHandler(private val isShowDiffWithLocal: () -> Boolean) : ChangesTreeDiffPreviewHandler() {
  override fun iterateSelectedChanges(tree: ChangesTree): JBIterable<Wrapper> {
    return collectWrappers(VcsTreeModelData.selected(tree))
  }

  override fun iterateAllChanges(tree: ChangesTree): JBIterable<Wrapper> {
    return collectWrappers(VcsTreeModelData.all(tree))
  }

  override fun selectChange(tree: ChangesTree, change: Wrapper) {
    ChangesBrowserBase.selectObjectWithTag(tree, change.userObject, change.tag)
  }

  private fun collectWrappers(treeModelData: VcsTreeModelData): JBIterable<Wrapper> {
    return treeModelData.iterateUserObjects(SavedPatchesProvider.ChangeObject::class.java)
      .map { SavedPatchChangeWrapper(it, isShowDiffWithLocal) }
  }
}

private class SavedPatchChangeWrapper(private val change: SavedPatchesProvider.ChangeObject,
                                      private val isShowDiffWithLocal: () -> Boolean)
  : Wrapper(), PresentableChange by change {

  override fun getUserObject(): Any = change
  override fun getPresentableName(): @Nls String = change.filePath.name
  override fun createProducer(project: Project?): DiffRequestProducer? {
    if (isShowDiffWithLocal()) return change.createDiffWithLocalRequestProducer(project, useBeforeVersion = false)
    return change.createDiffRequestProducer(project)
  }
  override fun getTag(): ChangesBrowserNode.Tag? = change.tag
}