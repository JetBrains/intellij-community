// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf;

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.util.DiffPlaces
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.DiffPreviewUpdateProcessor
import com.intellij.openapi.vcs.changes.shelf.DiffShelvedChangesActionProvider.PatchesPreloader
import com.intellij.openapi.vcs.changes.shelf.ShelvedWrapperDiffRequestProducer
import com.intellij.openapi.vcs.changes.ui.TreeHandlerChangesTreeTracker
import com.intellij.openapi.vcs.changes.ui.TreeHandlerDiffRequestProcessor
import com.intellij.util.concurrency.annotations.RequiresEdt

internal class ShelvedPreviewProcessor(
  project: Project,
  tree: ShelfTree,
  private val isInEditor: Boolean,
) : TreeHandlerDiffRequestProcessor(
  DiffPlaces.SHELVE_VIEW, tree, ShelveTreeDiffPreviewHandler.Companion.INSTANCE), DiffPreviewUpdateProcessor {

  private val preloader = PatchesPreloader(project)

  init {
    putContextUserData(PatchesPreloader.SHELF_PRELOADER, preloader)
    TreeHandlerChangesTreeTracker(tree, this, ShelveTreeDiffPreviewHandler.Companion.INSTANCE, !isInEditor).track()
  }

  @RequiresEdt
  override fun clear() {
    setCurrentChange(null)
    dropCaches()
  }

  override fun shouldAddToolbarBottomBorder(toolbarComponents: FrameDiffTool.ToolbarComponents): Boolean {
    return !isInEditor || super.shouldAddToolbarBottomBorder(toolbarComponents)
  }

  override fun loadRequestFast(provider: DiffRequestProducer): DiffRequest? {
    if (provider is ShelvedWrapperDiffRequestProducer) {
      val shelvedChange = provider.wrapper.shelvedChange
      if (shelvedChange != null && preloader.isPatchFileChanged(shelvedChange.patchPath)) return null
    }

    return super.loadRequestFast(provider)
  }
}
