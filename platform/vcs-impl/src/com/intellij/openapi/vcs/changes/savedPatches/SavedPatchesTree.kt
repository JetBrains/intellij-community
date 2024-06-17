// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.ide.impl.DataValidators
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshotProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.allUnder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.render.RenderingHelper
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.ui.tree.TreeVisitor
import com.intellij.ui.tree.TreeVisitor.Action
import com.intellij.util.FontUtil
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

class SavedPatchesTree(project: Project,
                       private val savedPatchesProviders: List<SavedPatchesProvider<*>>,
                       private val isProviderVisible: (SavedPatchesProvider<*>) -> Boolean,
                       parentDisposable: Disposable) : AsyncChangesTree(project, false, false, false) {
  internal val speedSearch: SpeedSearchSupply
  override val changesTreeModel: AsyncChangesTreeModel = SavedPatchesTreeModel()

  internal val visibleProvidersList get() = savedPatchesProviders.filter { isProviderVisible(it) }

  init {
    val nodeRenderer = ChangesBrowserNodeRenderer(myProject, { isShowFlatten }, false)
    setCellRenderer(MyTreeRenderer(nodeRenderer))
    putClientProperty(RenderingHelper.SHRINK_LONG_SELECTION, true)
    putClientProperty(RenderingHelper.SHRINK_LONG_RENDERER, true)

    treeStateStrategy = SavedPatchesTreeStateStrategy
    isScrollToSelection = false
    setEmptyText(VcsBundle.message("saved.patch.empty.text"))
    speedSearch = MySpeedSearch.installOn(this)

    savedPatchesProviders.forEach { provider ->
      provider.subscribeToPatchesListChanges(parentDisposable) {
        if (isProviderVisible(provider)) rebuildTree()
      }
    }

    Disposer.register(parentDisposable) { shutdown() }
  }

  override fun installGroupingSupport(): ChangesGroupingSupport {
    return ChangesGroupingSupport.Disabled(myProject, this)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    if (visibleProvidersList.isNotEmpty()) {
      val selectedObjects = selectedPatchObjects()
      visibleProvidersList.forEach { provider ->
        DataSink.uiDataSnapshot(sink, object : DataSnapshotProvider, DataValidators.SourceWrapper {
          override fun dataSnapshot(sink: DataSink) = provider.uiDataSnapshot(sink, selectedObjects)
          override fun unwrapSource(): Any = provider
        })
      }
    }
  }

  internal fun selectedPatchObjects(): Iterable<SavedPatchesProvider.PatchObject<*>> {
    return VcsTreeModelData.selected(this)
      .iterateUserObjects(SavedPatchesProvider.PatchObject::class.java)
  }

  override fun getToggleClickCount(): Int = 2

  private fun findNodeForProvider(provider: SavedPatchesProvider<*>): ChangesBrowserNode<*>? {
    if (!isProviderVisible(provider)) return null
    return VcsTreeModelData.findTagNode(this, provider.tag) ?: root
  }

  private fun showFirstUnderNode(node: TreeNode) {
    if (!isRootVisible && node.parent == null) {
      TreeUtil.promiseSelectFirst(this)
      return
    }
    val treePath = TreeUtil.getPathFromRoot(node)
    TreeUtil.promiseSelect(this, TreeVisitor {
      if (it.isDescendant(treePath)) Action.CONTINUE
      else if (treePath.isDescendant(it)) Action.INTERRUPT
      else Action.SKIP_CHILDREN
    })
  }

  internal fun showFirstUnderProvider(provider: SavedPatchesProvider<*>) {
    val providerNode = findNodeForProvider(provider) ?: return
    showFirstUnderNode(providerNode)
  }

  internal fun showFirstUnderObject(provider: SavedPatchesProvider<*>, userObject: Any) {
    val providerNode = findNodeForProvider(provider) ?: return
    val node = TreeUtil.findNodeWithObject(providerNode, userObject) ?: providerNode
    showFirstUnderNode(node)
  }

  private inner class SavedPatchesTreeModel : SimpleAsyncChangesTreeModel() {
    override fun buildTreeModelSync(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel {
      val modelBuilder = TreeModelBuilder(project, grouping)
      val visibleProviders = visibleProvidersList
      if (visibleProviders.any { !it.isEmpty() }) {
        visibleProviders.forEach { provider -> provider.buildPatchesTree(modelBuilder, visibleProviders.size > 1) }
      }
      return modelBuilder.build()
    }
  }

  class TagWithCounterChangesBrowserNode(tag: Tag, expandByDefault: Boolean = true, private val sortWeight: Int? = null) :
    TagChangesBrowserNode(tag, SimpleTextAttributes.REGULAR_ATTRIBUTES, expandByDefault) {

    constructor(text: @Nls String, expandByDefault: Boolean = true, sortWeight: Int? = null) :
      this(TagImpl(text), expandByDefault, sortWeight)

    private val stashCount = ClearableLazyValue.create {
      allUnder(this).userObjects(SavedPatchesProvider.PatchObject::class.java).size
    }

    init {
      markAsHelperNode()
    }

    override fun getCountText() = FontUtil.spaceAndThinSpace() + stashCount.value
    override fun resetCounters() {
      super.resetCounters()
      stashCount.drop()
    }

    override fun getSortWeight(): Int {
      return sortWeight ?: super.getSortWeight()
    }
  }

  private class MyTreeRenderer(renderer: ChangesBrowserNodeRenderer) : ChangesTreeCellRenderer(renderer) {
    private val labelWrapper = Wrapper()

    init {
      add(labelWrapper, BorderLayout.EAST)
    }

    override fun getTreeCellRendererComponent(tree: JTree,
                                              value: Any,
                                              selected: Boolean,
                                              expanded: Boolean,
                                              leaf: Boolean,
                                              row: Int,
                                              hasFocus: Boolean): Component {
      val rendererComponent = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
      val node = value as ChangesBrowserNode<*>
      labelWrapper.setContent(getLabelComponent(tree as ChangesTree, node, row, selected))
      val speedSearch = SpeedSearchSupply.getSupply(tree)
      if (speedSearch != null) {
        val patchObject = node.userObject as? SavedPatchesProvider.PatchObject<*>
        if (patchObject != null) {
          val text = textRenderer.getCharSequence(false).toString()
          if (speedSearch.matchingFragments(text) == null && speedSearch.matches(patchObject)) {
            SpeedSearchUtil.applySpeedSearchHighlighting(textRenderer, listOf(TextRange.allOf(text)), selected)
          }
        }
      }
      return rendererComponent
    }

    private fun getLabelComponent(tree: ChangesTree, node: ChangesBrowserNode<*>, row: Int, selected: Boolean): JComponent? {
      val patchObject = node.userObject as? SavedPatchesProvider.PatchObject<*> ?: return null
      return patchObject.getLabelComponent(tree, row, selected)
    }
  }

  private class MySpeedSearch private constructor(tree: JTree) : TreeSpeedSearch(tree, true, null,
                                                                                 ChangesBrowserNode.TO_TEXT_CONVERTER) {
    override fun isMatchingElement(element: Any?, pattern: String?): Boolean {
      val isMatching = super.isMatchingElement(element, pattern)
      if (isMatching) return true
      val node = (element as? TreePath)?.lastPathComponent as? ChangesBrowserNode<*> ?: return false
      val patchObject = node.userObject as? SavedPatchesProvider.PatchObject<*> ?: return false
      return matches(patchObject)
    }

    companion object {
      fun installOn(tree: JTree): MySpeedSearch {
        val search = MySpeedSearch(tree)
        search.setupListeners()
        return search
      }
    }
  }

  companion object {
    private fun SpeedSearchSupply.matches(patchObject: SavedPatchesProvider.PatchObject<*>): Boolean {
      val changes = patchObject.cachedChanges() ?: return false
      return changes.any {
        matchingFragments(it.filePath.name) != null
      }
    }
  }

  private data class SavedPatchesTreeState(val treeState: TreeState, val wasEmpty: Boolean)

  private object SavedPatchesTreeStateStrategy : TreeStateStrategy<SavedPatchesTreeState> {
    override fun saveState(tree: ChangesTree): SavedPatchesTreeState {
      val treeState = TreeState.createOn(tree, true, true)
      val wasEmpty = VcsTreeModelData.all(tree).iterateUserObjects().isEmpty
      return SavedPatchesTreeState(treeState, wasEmpty)
    }

    override fun restoreState(tree: ChangesTree, state: SavedPatchesTreeState?, scrollToSelection: Boolean) {
      if (state == null) return

      state.treeState.setScrollToSelection(scrollToSelection)
      state.treeState.applyTo(tree)

      if (!VcsTreeModelData.all(tree).iterateUserObjects().isEmpty && state.wasEmpty) {
        tree.expandDefaults()
      }
      if (tree.selectionCount == 0) TreeUtil.selectFirstNode(tree)
    }
  }
}