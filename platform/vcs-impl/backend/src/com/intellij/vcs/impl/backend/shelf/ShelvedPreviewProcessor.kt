// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf;

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.util.DiffPlaces
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vcs.changes.DiffPreviewUpdateProcessor
import com.intellij.openapi.vcs.changes.shelf.DiffShelvedChangesActionProvider.PatchesPreloader
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.shelf.ShelvedWrapper
import com.intellij.openapi.vcs.changes.shelf.ShelvedWrapperDiffRequestProducer
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesTreeDiffPreviewHandler
import com.intellij.openapi.vcs.changes.ui.TreeHandlerChangesTreeTracker
import com.intellij.openapi.vcs.changes.ui.TreeHandlerDiffRequestProcessor
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.util.Function
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.tree.DefaultMutableTreeNode

internal class ShelvedPreviewProcessor(project: Project, tree: ShelfTree, isInEditor: Boolean) : TreeHandlerDiffRequestProcessor(
  DiffPlaces.SHELVE_VIEW, tree, ShelveTreeDiffPreviewHandler.Companion.INSTANCE), DiffPreviewUpdateProcessor {
  private val myIsInEditor: Boolean

  private val myPreloader: PatchesPreloader

  init {
    myIsInEditor = isInEditor
    myPreloader = PatchesPreloader(project)

    putContextUserData<PatchesPreloader?>(PatchesPreloader.SHELF_PRELOADER, myPreloader)

    TreeHandlerChangesTreeTracker(tree, this, ShelveTreeDiffPreviewHandler.Companion.INSTANCE, !isInEditor).track()
  }

  @RequiresEdt
  override fun clear() {
    setCurrentChange(null)
    dropCaches()
  }

  override fun shouldAddToolbarBottomBorder(toolbarComponents: FrameDiffTool.ToolbarComponents): Boolean {
    return !myIsInEditor || super.shouldAddToolbarBottomBorder(toolbarComponents)
  }

  override fun loadRequestFast(provider: DiffRequestProducer): DiffRequest? {
    if (provider is ShelvedWrapperDiffRequestProducer) {
      val shelvedChange = provider.getWrapper().getShelvedChange()
      if (shelvedChange != null && myPreloader.isPatchFileChanged(shelvedChange.getPatchPath())) return null
    }

    return super.loadRequestFast(provider)
  }

  private class ShelveTreeDiffPreviewHandler : ChangesTreeDiffPreviewHandler() {
    public override fun iterateSelectedChanges(tree: ChangesTree): Iterable<out Wrapper?> {
      return VcsTreeModelData.selected(tree).iterateUserObjects<ShelvedWrapper?>(ShelvedWrapper::class.java)
    }

    public override fun iterateAllChanges(tree: ChangesTree): Iterable<out Wrapper?> {
      val changeLists =
        VcsTreeModelData.selected(tree).iterateUserObjects<ShelvedWrapper?>(ShelvedWrapper::class.java)
          .map<ShelvedChangeList>(Function { wrapper: ShelvedWrapper? -> wrapper!!.getChangeList() })
          .toSet()

      return VcsTreeModelData.all(tree).iterateRawNodes()
        .filter(Condition { node: ChangesBrowserNode<*>? -> node is ShelvedListNode && changeLists.contains(node.getList()) })
        .flatMap<ShelvedWrapper?>(Function { node: ChangesBrowserNode<*>? ->
          VcsTreeModelData.allUnder(node!!).iterateUserObjects<ShelvedWrapper?>(ShelvedWrapper::class.java)
        })
    }

    public override fun selectChange(tree: ChangesTree, change: Wrapper) {
      if (change is ShelvedWrapper) {
        val root: DefaultMutableTreeNode = tree.getRoot()
        val changelistNode = TreeUtil.findNodeWithObject(root, change.getChangeList())
        if (changelistNode == null) return

        val node = TreeUtil.findNodeWithObject(changelistNode, change)
        if (node == null) return
        TreeUtil.selectPath(tree, TreeUtil.getPathFromRoot(node), false)
      }
    }

    companion object {
      val INSTANCE: ShelveTreeDiffPreviewHandler = ShelveTreeDiffPreviewHandler()
    }
  }
}
