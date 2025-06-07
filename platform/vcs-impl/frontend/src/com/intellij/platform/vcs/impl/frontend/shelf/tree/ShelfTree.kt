// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf.tree

import com.intellij.ide.DeleteProvider
import com.intellij.openapi.actionSystem.CommonDataKeys.NAVIGATABLE_ARRAY
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataKey.Companion.create
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.withLastKnownDb
import com.intellij.platform.vcs.impl.frontend.changes.CHANGE_LISTS_KEY
import com.intellij.platform.vcs.impl.frontend.changes.ChangeList
import com.intellij.platform.vcs.impl.frontend.changes.ChangesTree
import com.intellij.platform.vcs.impl.frontend.changes.ExactlySelectedData
import com.intellij.platform.vcs.impl.frontend.changes.SelectedData
import com.intellij.ui.TreeSpeedSearch
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.platform.vcs.impl.frontend.navigation.FrontendShelfNavigatable
import com.intellij.platform.vcs.impl.shared.changes.GroupingUpdatePlaces
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.TreePath

@ApiStatus.Internal
class ShelfTree(project: Project, cs: CoroutineScope) : ChangesTree(project, cs, GroupingUpdatePlaces.SHELF_TREE) {

  private val deleteProvider: DeleteProvider = ShelveDeleteProvider(project, this)

  init {
    TreeSpeedSearch.installOn(this, true) { (it.lastPathComponent as EntityChangesBrowserNode<*>).textPresentation }
  }

  override fun isPathEditable(path: TreePath): Boolean {
    return isEditable && selectionCount == 1 && path.lastPathComponent is ShelvedChangeListNode
  }

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    withLastKnownDb {
      sink[SHELVED_CHANGES_TREE_KEY] = this
      val selectedLists = getSelectedLists()
      sink[SELECTED_CHANGELISTS_KEY] = selectedLists
      sink[SELECTED_DELETED_CHANGELISTS_KEY] = selectedLists.filter { it.isDeleted }.toSet()
      sink[SELECTED_CHANGES_KEY] = getSelectedChanges()
      val groupedChanges = getSelectedChangesWithChangeLists()
      sink[GROUPED_CHANGES_KEY] = groupedChanges
      sink[CHANGE_LISTS_KEY] = groupedChanges.map { ChangeList(it.key, it.value) }
      if (!isEditing()) {
        sink[PlatformDataKeys.DELETE_ELEMENT_PROVIDER] = deleteProvider
      }
      sink[NAVIGATABLE_ARRAY] = arrayOf(FrontendShelfNavigatable(project, groupedChanges))
    }
  }

  override fun shouldShowBusyIconIfNeeded(): Boolean = true

  private fun ChangesBrowserNode<*>.findParentOfType(clazz: Class<*>): ChangesBrowserNode<*>? {
    var parent = this
    while (true) {
      if (clazz.isInstance(parent)) return parent
      parent = parent.parent ?: return null
    }
  }

  fun getSelectedChangesWithChangeLists(): Map<ShelvedChangeListEntity, List<ShelvedChangeEntity>> {
    return getSelectedChangeNodesGrouped()
      .map { entry ->
        entry.key.userObject as ShelvedChangeListEntity to entry.value.map { it.userObject as ShelvedChangeEntity }
      }.toMap()
  }

  fun getSelectedChangeNodesGrouped(): Map<ShelvedChangeListNode, List<ShelvedChangeNode>> {
    return SelectedData(this).iterateRawNodes()
      .filter { it.userObject is ShelvedChangeEntity }
      .filterIsInstance<ShelvedChangeNode>()
      .groupBy { it.findParentOfType(ShelvedChangeListNode::class.java) as ShelvedChangeListNode }
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
    val SELECTED_DELETED_CHANGELISTS_KEY: DataKey<Set<ShelvedChangeListEntity>> = create("ShelveChangesManager.SelectedDeletedChangelists")
    val GROUPED_CHANGES_KEY: DataKey<Map<ShelvedChangeListEntity, List<ShelvedChangeEntity>>> = create("ShelveChangesManager.GroupedChanges")
  }
}