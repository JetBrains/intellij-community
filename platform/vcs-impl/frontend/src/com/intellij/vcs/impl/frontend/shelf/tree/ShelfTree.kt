// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.shelf.tree

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataKey.Companion.create
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.project.Project
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.impl.frontend.changes.CHANGE_LISTS_KEY
import com.intellij.vcs.impl.frontend.changes.ChangeList
import com.intellij.vcs.impl.frontend.changes.ChangesTree
import com.intellij.vcs.impl.frontend.changes.ExactlySelectedData
import com.intellij.vcs.impl.frontend.changes.SelectedData
import com.intellij.vcs.impl.shared.changes.GroupingUpdatePlaces
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import kotlinx.coroutines.CoroutineScope
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class ShelfTree(project: Project, private val treeRoot: ChangesBrowserRootNode, cs: CoroutineScope) : ChangesTree(project, cs, GroupingUpdatePlaces.SHELF_TREE) {

  override fun isPathEditable(path: TreePath): Boolean {
    return isEditable && selectionCount == 1 && path.lastPathComponent is ShelvedChangeListNode
  }

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    sink[SHELVED_CHANGES_TREE_KEY] = this
    sink[SELECTED_CHANGELISTS_KEY] = getSelectedLists()
    sink[SELECTED_CHANGES_KEY] = getSelectedChanges()
    val groupedChanges = SelectedData(this).iterateRawNodes()
      .filter { it.userObject is ShelvedChangeEntity }
      .groupBy { it.findParentOfType(ShelvedChangeListNode::class.java) }
      .map { entry ->
        entry.key?.userObject as ShelvedChangeListEntity to entry.value.map { it.userObject as ShelvedChangeEntity }
      }
    sink[GROUPED_CHANGES_KEY] = groupedChanges.toMap()
    sink[CHANGE_LISTS_KEY] = groupedChanges.map { ChangeList(it.first, it.second) }
  }

  fun rebuildTree() {
    updateTreeModel(DefaultTreeModel(treeRoot))
  }

  private fun ChangesBrowserNode<*>.findParentOfType(clazz: Class<*>): ChangesBrowserNode<*>? {
    var parent = this
    while (true) {
      if (clazz.isInstance(parent)) return parent
      parent = parent.parent as? ChangesBrowserNode ?: return null
    }
  }

  fun getSelectedLists(): Set<ShelvedChangeListEntity> {
    return selectionPaths?.mapNotNull { TreeUtil.findObjectInPath(it, ShelvedChangeListEntity::class.java) }?.toSet() ?: emptySet()
  }

  fun getSelectedChanges(): Set<ShelvedChangeEntity> {
    return SelectedData(this).iterateUserObjects(ShelvedChangeEntity::class.java).toSet()
  }

  fun getExactlySelectedLists(): Set<ShelvedChangeListEntity> {
    return ExactlySelectedData(this).iterateUserObjects(ShelvedChangeListEntity::class.java).toSet();
  }

  companion object {
    val SHELVED_CHANGES_TREE_KEY: DataKey<ShelfTree> = create("ShelveChangesManager.ShelvedChangesTree")
    val SELECTED_CHANGES_KEY: DataKey<Set<ShelvedChangeEntity>> = create("ShelveChangesManager.SelectedChanges")
    val SELECTED_CHANGELISTS_KEY: DataKey<Set<ShelvedChangeListEntity>> = create("ShelveChangesManager.SelectedChangelists")
    val GROUPED_CHANGES_KEY: DataKey<Map<ShelvedChangeListEntity, List<ShelvedChangeEntity>>> = create("ShelveChangesManager.GroupedChanges")
  }
}