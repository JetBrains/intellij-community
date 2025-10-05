// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.shelf.diff

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.util.DiffPlaces
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.shelf.DiffShelvedChangesActionProvider.PatchesPreloader
import com.intellij.openapi.vcs.changes.shelf.ShelvedWrapper
import com.intellij.openapi.vcs.changes.shelf.ShelvedWrapperDiffRequestProducer
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.platform.project.asEntity
import com.intellij.vcs.shelf.ShelfTreeHolder
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.shelf.ShelvedListNode
import com.intellij.platform.vcs.impl.shared.changes.DiffPreviewUpdateProcessor
import com.intellij.platform.vcs.impl.shared.rhizome.SelectShelveChangeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import com.jetbrains.rhizomedb.entity
import fleet.kernel.change
import fleet.kernel.shared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShelvedPreviewProcessor(
  project: Project,
  private val cs: CoroutineScope,
  private val isInEditor: Boolean,
  private val changesProvider: ShelfDiffChangesProvider,
) : ChangeViewDiffRequestProcessor(project, DiffPlaces.SHELVE_VIEW), DiffPreviewUpdateProcessor {

  private val preloader = PatchesPreloader(project)

  init {
    putContextUserData(PatchesPreloader.SHELF_PRELOADER, preloader)
    cs.launch {
      changesProvider.changesStateFlow.collectLatest {
        cs.launch(Dispatchers.EDT) {
          updatePreview(true, it.fromModelChange)
        }
      }
    }
  }

  override fun iterateSelectedChanges(): Iterable<Wrapper> {
    return changesProvider.getSelectedChanges()
  }

  override fun iterateAllChanges(): Iterable<Wrapper> {
    return changesProvider.getAllChanges()
  }

  @RequiresEdt
  override fun clear() {
    setCurrentChange(null)
    dropCaches()
  }

  override fun selectChange(change: Wrapper) {
    if (change is ShelvedWrapper) {
      cs.launch {
        val nodeToSelect = TreeUtil.findNodeWithObject(changesProvider.treeModel.root as ChangesBrowserNode<*>, change) as? ChangesBrowserNode<*>
                           ?: return@launch
        val changeListNode = nodeToSelect.path.firstOrNull { it is ShelvedListNode } as? ChangesBrowserNode<*> ?: return@launch
        val changeEntity = nodeToSelect.getUserData(ShelfTreeHolder.Companion.ENTITY_ID_KEY)?.derefOrNull() as? ShelvedChangeEntity
                           ?: return@launch
        val changeListEntity = changeListNode.getUserData(ShelfTreeHolder.Companion.ENTITY_ID_KEY)?.derefOrNull() as? ShelvedChangeListEntity
                               ?: return@launch
        val projectEntity = project.asEntity()
        change {
          shared {
            entity(SelectShelveChangeEntity.Project, projectEntity)?.delete()
            SelectShelveChangeEntity.new {
              it[SelectShelveChangeEntity.Change] = changeEntity
              it[SelectShelveChangeEntity.ChangeList] = changeListEntity
              it[SelectShelveChangeEntity.Project] = projectEntity
            }
          }
        }
      }
    }
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
