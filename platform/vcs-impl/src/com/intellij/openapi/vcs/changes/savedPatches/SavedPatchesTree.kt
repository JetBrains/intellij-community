// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil.performActionDumbAwareWithCallbacks
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.savedPatches.SavedPatchesUi.Companion.SAVED_PATCHES_UI_PLACE
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import com.intellij.util.Processor
import com.intellij.util.ui.tree.TreeUtil
import one.util.streamex.StreamEx
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.util.stream.Stream
import javax.swing.JTree

class SavedPatchesTree(project: Project,
                       private val savedPatchesProviders: List<SavedPatchesProvider<*>>,
                       parentDisposable: Disposable) : ChangesTree(project, false, false) {
  init {
    val nodeRenderer = ChangesBrowserNodeRenderer(myProject, { isShowFlatten }, false)
    setCellRenderer(MyTreeRenderer(nodeRenderer))

    isKeepTreeState = true
    isScrollToSelection = false
    setEmptyText(VcsBundle.message("saved.patch.empty.text"))

    doubleClickHandler = Processor { e ->
      val diffAction = ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_DIFF_COMMON)

      val dataContext = DataManager.getInstance().getDataContext(this)
      val event = AnActionEvent.createFromAnAction(diffAction, e, SAVED_PATCHES_UI_PLACE, dataContext)
      val isEnabled = ActionUtil.lastUpdateAndCheckDumb(diffAction, event, true)
      if (isEnabled) performActionDumbAwareWithCallbacks(diffAction, event)

      isEnabled
    }

    savedPatchesProviders.forEach { provider -> provider.subscribeToPatchesListChanges(parentDisposable, ::rebuildTree) }
  }

  override fun rebuildTree() {
    val modelBuilder = TreeModelBuilder(project, groupingSupport.grouping)
    savedPatchesProviders.forEach { provider -> provider.buildPatchesTree(modelBuilder) }
    updateTreeModel(modelBuilder.build())

    if (selectionCount == 0) {
      TreeUtil.selectFirstNode(this)
    }
  }

  override fun installGroupingSupport(): ChangesGroupingSupport {
    return object : ChangesGroupingSupport(myProject, this, false) {
      override fun isAvailable(groupingKey: String): Boolean = false
    }
  }

  override fun getData(dataId: String): Any? {
    val provider = savedPatchesProviders.find { it.dataKey.`is`(dataId) }
    if (provider != null) {
      return StreamEx.of(selectedPatchObjects().map(SavedPatchesProvider.PatchObject<*>::data))
        .filterIsInstance(provider.dataClass)
        .toList()
    }
    if (CommonDataKeys.PROJECT.`is`(dataId)) return myProject
    return super.getData(dataId)
  }

  internal fun selectedPatchObjects(): Stream<SavedPatchesProvider.PatchObject<*>> {
    return VcsTreeModelData.selected(this).userObjectsStream(SavedPatchesProvider.PatchObject::class.java)
  }

  class TagWithCounterChangesBrowserNode(text: @Nls String, private val sortWeight: Int? = null) :
    TagChangesBrowserNode(TagImpl(text), SimpleTextAttributes.REGULAR_ATTRIBUTES, true) {
    private val stashCount = ClearableLazyValue.create {
      VcsTreeModelData.children(this).userObjects(SavedPatchesProvider.PatchObject::class.java).size
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
    private var painter: SavedPatchesProvider.PatchObject.Painter? = null

    override fun paint(g: Graphics) {
      super.paint(g)
      painter?.paint(g as Graphics2D)
    }

    override fun getTreeCellRendererComponent(tree: JTree,
                                              value: Any,
                                              selected: Boolean,
                                              expanded: Boolean,
                                              leaf: Boolean,
                                              row: Int,
                                              hasFocus: Boolean): Component {
      val rendererComponent = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
      painter = customizePainter(tree as ChangesTree, value as ChangesBrowserNode<*>, row, selected)
      return rendererComponent
    }

    private fun customizePainter(tree: ChangesTree,
                                 node: ChangesBrowserNode<*>,
                                 row: Int,
                                 selected: Boolean): SavedPatchesProvider.PatchObject.Painter? {
      if (tree.expandableItemsHandler.expandedItems.contains(row)) {
        return null
      }

      val patchObject = node.userObject as? SavedPatchesProvider.PatchObject<*> ?: return null
      return patchObject.createPainter(tree, this, row, selected)
    }
  }
}