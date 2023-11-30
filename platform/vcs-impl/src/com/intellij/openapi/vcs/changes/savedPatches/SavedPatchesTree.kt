// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.ide.DataManager
import com.intellij.ide.util.treeView.TreeState
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil.performActionDumbAwareWithCallbacks
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.savedPatches.SavedPatchesUi.Companion.SAVED_PATCHES_UI_PLACE
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.allUnder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.FontUtil
import com.intellij.util.Processor
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.util.stream.Stream
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class SavedPatchesTree(project: Project,
                       private val savedPatchesProviders: List<SavedPatchesProvider<*>>,
                       parentDisposable: Disposable) : AsyncChangesTree(project, false, false, false) {
  internal val speedSearch: SpeedSearchSupply
  override val changesTreeModel: AsyncChangesTreeModel = SavedPatchesTreeModel()

  init {
    val nodeRenderer = ChangesBrowserNodeRenderer(myProject, { isShowFlatten }, false)
    setCellRenderer(MyTreeRenderer(nodeRenderer))

    treeStateStrategy = SavedPatchesTreeStateStrategy
    isScrollToSelection = false
    setEmptyText(VcsBundle.message("saved.patch.empty.text"))
    speedSearch = MySpeedSearch.installOn(this)

    doubleClickHandler = Processor { e ->
      if (EditSourceOnDoubleClickHandler.isToggleEvent(this, e)) return@Processor false

      val diffAction = ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_DIFF_COMMON)

      val dataContext = DataManager.getInstance().getDataContext(this)
      val event = AnActionEvent.createFromAnAction(diffAction, e, SAVED_PATCHES_UI_PLACE, dataContext)
      val isEnabled = ActionUtil.lastUpdateAndCheckDumb(diffAction, event, true)
      if (isEnabled) performActionDumbAwareWithCallbacks(diffAction, event)

      isEnabled
    }

    savedPatchesProviders.forEach { provider -> provider.subscribeToPatchesListChanges(parentDisposable, ::rebuildTree) }
  }

  override fun installGroupingSupport(): ChangesGroupingSupport {
    return ChangesGroupingSupport.Disabled(myProject, this)
  }

  override fun getData(dataId: String): Any? {
    val selectedObjects = selectedPatchObjects()
    val data = savedPatchesProviders.firstNotNullOfOrNull { provider -> provider.getData(dataId, selectedObjects) }
    if (data != null) return data
    if (CommonDataKeys.PROJECT.`is`(dataId)) return myProject
    return super.getData(dataId)
  }

  internal fun selectedPatchObjects(): Stream<SavedPatchesProvider.PatchObject<*>> {
    return VcsTreeModelData.selected(this)
      .iterateUserObjects(SavedPatchesProvider.PatchObject::class.java)
      .toStream()
  }

  override fun getToggleClickCount(): Int = 2

  private inner class SavedPatchesTreeModel : SimpleAsyncChangesTreeModel() {
    override fun buildTreeModelSync(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel {
      val modelBuilder = TreeModelBuilder(project, grouping)
      if (savedPatchesProviders.any { !it.isEmpty() }) {
        savedPatchesProviders.forEach { provider -> provider.buildPatchesTree(modelBuilder) }
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
      val node = value as ChangesBrowserNode<*>
      painter = customizePainter(tree as ChangesTree, node, row, selected)
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