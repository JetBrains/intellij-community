// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor.Wrapper
import com.intellij.openapi.vcs.changes.savedPatches.SavedPatchesUi.Companion.SAVED_PATCHES_UI_PLACE
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.util.containers.JBIterable
import org.jetbrains.annotations.Nls

class SavedPatchesDiffProcessor(tree: ChangesTree,
                                private val isInEditor: Boolean)
  : TreeHandlerDiffRequestProcessor(SAVED_PATCHES_UI_PLACE, tree, SavedPatchesDiffPreviewHandler) {
  init {
    TreeHandlerChangesTreeTracker(tree, this, handler, !isInEditor).track()
  }

  override fun shouldAddToolbarBottomBorder(toolbarComponents: FrameDiffTool.ToolbarComponents): Boolean {
    return !isInEditor || super.shouldAddToolbarBottomBorder(toolbarComponents)
  }
}

object SavedPatchesDiffPreviewHandler : ChangesTreeDiffPreviewHandler() {
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
      .map { SavedPatchChangeWrapper(it) }
  }
}

private class SavedPatchChangeWrapper(private val change: SavedPatchesProvider.ChangeObject)
  : Wrapper(), PresentableChange by change {

  override fun getUserObject(): Any = change
  override fun getPresentableName(): @Nls String = change.filePath.name
  override fun createProducer(project: Project?): DiffRequestProducer? = change.createDiffRequestProducer(project)
  override fun getTag(): ChangesBrowserNode.Tag? = change.tag
}