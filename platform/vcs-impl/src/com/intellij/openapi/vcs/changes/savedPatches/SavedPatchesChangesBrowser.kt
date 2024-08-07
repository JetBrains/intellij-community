// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.containers.JBIterable
import com.intellij.util.ui.StatusText
import java.util.concurrent.CompletableFuture
import javax.swing.tree.DefaultTreeModel

class SavedPatchesChangesBrowser(project: Project, internal val isShowDiffWithLocal: () -> Boolean, parentDisposable: Disposable)
  : AsyncChangesBrowserBase(project, false, false), Disposable {

  var changes: Collection<SavedPatchesProvider.ChangeObject> = emptyList()
    private set

  var currentPatchObject: SavedPatchesProvider.PatchObject<*>? = null
    private set

  private var currentChangesFuture: CompletableFuture<SavedPatchesProvider.LoadingResult>? = null

  init {
    init()
    viewer.emptyText.text = VcsBundle.message("saved.patch.changes.empty")
    hideViewerBorder()

    Disposer.register(parentDisposable, this)
  }

  fun <S> selectPatchObject(patchObject: SavedPatchesProvider.PatchObject<S>?) {
    if (patchObject == currentPatchObject) return
    currentPatchObject = patchObject
    currentChangesFuture = null

    if (patchObject == null) {
      setEmpty { statusText -> statusText.text = VcsBundle.message("saved.patch.changes.empty") }
      return
    }

    setEmpty { statusText -> statusText.text = VcsBundle.message("saved.patch.changes.loading") }

    val futureChanges = patchObject.loadChanges() ?: return
    currentChangesFuture = futureChanges
    futureChanges.thenRunAsync(Runnable {
      if (currentPatchObject != patchObject) return@Runnable

      when (val data = currentChangesFuture?.get()) {
        is SavedPatchesProvider.LoadingResult.Changes -> {
          setData(data.changes)
        }
        is SavedPatchesProvider.LoadingResult.Error -> {
          setEmpty { statusText -> statusText.setText(data.message, SimpleTextAttributes.ERROR_ATTRIBUTES) }
        }
        null -> {}
      }
      currentChangesFuture = null
    }, EdtExecutorService.getInstance())
  }

  override fun createPopupMenuActions(): List<AnAction> {
    return super.createPopupMenuActions() + ActionManager.getInstance().getAction("Vcs.SavedPatches.ChangesBrowser.ContextMenu")
  }

  override fun createToolbarActions(): List<AnAction> {
    return super.createToolbarActions() + ActionManager.getInstance().getAction("Vcs.SavedPatches.ChangesBrowser.Toolbar")
  }

  override val changesTreeModel: AsyncChangesTreeModel = object : SimpleAsyncChangesTreeModel() {
    override fun buildTreeModelSync(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel {
      val builder = TreeModelBuilder(myProject, grouping)
      val groupedChanges = changes.groupBy { it.tag }
      for ((tag, changes) in groupedChanges) {
        if (changes.isEmpty()) continue
        val root = if (tag == null) builder.myRoot else builder.createTagNode(tag, SimpleTextAttributes.REGULAR_ATTRIBUTES, false)
        changes.forEach { change ->
          builder.insertChangeNode(change.filePath, root, ChangeObjectNode(change))
        }
      }
      return builder.build()
    }
  }

  private fun setEmpty(updateEmptyText: (StatusText) -> Unit) = setData(emptyList(), updateEmptyText)

  private fun setData(changeObjects: Collection<SavedPatchesProvider.ChangeObject>) {
    setData(changeObjects) { statusText -> statusText.text = "" }
  }

  private fun setData(changeObjects: Collection<SavedPatchesProvider.ChangeObject>,
                      updateEmptyText: (StatusText) -> Unit) {
    changes = changeObjects
    updateEmptyText(viewer.emptyText)
    viewer.rebuildTree()
  }

  public override fun getDiffRequestProducer(userObject: Any): ChangeDiffRequestChain.Producer? {
    if (userObject !is SavedPatchesProvider.ChangeObject) return null
    if (isShowDiffWithLocal()) return userObject.createDiffWithLocalRequestProducer(myProject, useBeforeVersion = false)
    return userObject.createDiffRequestProducer(myProject)
  }

  fun getDiffWithLocalRequestProducer(userObject: Any, useBeforeVersion: Boolean): ChangeDiffRequestChain.Producer? {
    if (userObject !is SavedPatchesProvider.ChangeObject) return null
    return userObject.createDiffWithLocalRequestProducer(myProject, useBeforeVersion)
  }

  private fun VcsTreeModelData.mapToChange(): JBIterable<Change> {
    return iterateUserObjects(SavedPatchesProvider.ChangeObject::class.java)
      .map { it.asChange() }
      .filterNotNull()
  }

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    val selection = VcsTreeModelData.selected(myViewer)

    val changes = selection.mapToChange().toList().toTypedArray()
    sink[VcsDataKeys.CHANGES] =
      if (changes.isNotEmpty()) changes
      else VcsTreeModelData.all(myViewer).mapToChange().toList().toTypedArray()
    sink[VcsDataKeys.SELECTED_CHANGES] = changes
    sink[VcsDataKeys.SELECTED_CHANGES_IN_DETAILS] = changes
    sink[VcsDataKeys.CHANGES_SELECTION] =
      VcsTreeModelData.getListSelectionOrAll(myViewer)
        .map { (it as? SavedPatchesProvider.ChangeObject)?.asChange() }
    sink[VcsDataKeys.CHANGE_LEAD_SELECTION] =
      VcsTreeModelData.exactlySelected(myViewer).mapToChange().toList().toTypedArray()

    val changeObjects = selection.iterateUserObjects(SavedPatchesProvider.ChangeObject::class.java)
    sink[CommonDataKeys.VIRTUAL_FILE_ARRAY] = changeObjects
      .filterMap { it.filePath.virtualFile }
      .toList().toTypedArray()
    sink[VcsDataKeys.FILE_PATHS] = changeObjects.map { it.filePath }
    sink[CommonDataKeys.NAVIGATABLE_ARRAY] = changeObjects
      .filterMap { it.filePath.virtualFile }
      .let { ChangesUtil.getNavigatableArray(myProject, it) }
    sink[SavedPatchesUi.SAVED_PATCH_SELECTED_CHANGES] = changeObjects

    sink[SavedPatchesUi.SAVED_PATCH_CHANGES] = getSavedPatchChanges()
  }

  internal fun getSavedPatchChanges(): Iterable<SavedPatchesProvider.ChangeObject> = VcsTreeModelData.all(myViewer)
    .iterateUserObjects(SavedPatchesProvider.ChangeObject::class.java)

  override fun dispose() {
    shutdown()
  }

  private class ChangeObjectNode(change: SavedPatchesProvider.ChangeObject) :
    AbstractChangesBrowserFilePathNode<SavedPatchesProvider.ChangeObject>(change, change.fileStatus) {
    override fun filePath(userObject: SavedPatchesProvider.ChangeObject): FilePath = userObject.filePath
    override fun originPath(userObject: SavedPatchesProvider.ChangeObject): FilePath? = userObject.originalFilePath
  }
}