// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.DiffPreview
import com.intellij.openapi.vcs.changes.EditorTabPreview
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.containers.JBIterable
import com.intellij.util.ui.StatusText
import java.awt.Component
import java.util.concurrent.CompletableFuture
import javax.swing.tree.DefaultTreeModel

class SavedPatchesChangesBrowser(project: Project,
                                 private val focusMainUi: (Component?) -> Unit,
                                 parentDisposable: Disposable)
  : AsyncChangesBrowserBase(project, false, false), Disposable {

  var changes: Collection<SavedPatchesProvider.ChangeObject> = emptyList()
    private set

  private var currentPatchObject: SavedPatchesProvider.PatchObject<*>? = null
  private var currentChangesFuture: CompletableFuture<SavedPatchesProvider.LoadingResult>? = null

  private var diffPreviewProcessor: SavedPatchesDiffPreview? = null
  var editorTabPreview: EditorTabPreview? = null
    private set

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
          setEmpty { statusText -> statusText.setText(data.error.localizedMessage, SimpleTextAttributes.ERROR_ATTRIBUTES) }
        }
        null -> {}
      }
      currentChangesFuture = null
    }, EdtExecutorService.getInstance())
  }

  override fun createPopupMenuActions(): List<AnAction> {
    return super.createPopupMenuActions() + ActionManager.getInstance().getAction("Vcs.SavedPatches.ChangesBrowser.ContextMenu")
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

  override fun getShowDiffActionPreview(): DiffPreview? {
    return editorTabPreview
  }

  public override fun getDiffRequestProducer(userObject: Any): ChangeDiffRequestChain.Producer? {
    if (userObject !is SavedPatchesProvider.ChangeObject) return null
    return userObject.createDiffRequestProducer(myProject)
  }

  fun getDiffWithLocalRequestProducer(userObject: Any, useBeforeVersion: Boolean): ChangeDiffRequestChain.Producer? {
    if (userObject !is SavedPatchesProvider.ChangeObject) return null
    return userObject.createDiffWithLocalRequestProducer(myProject, useBeforeVersion)
  }

  fun installDiffPreview(isInEditor: Boolean): SavedPatchesDiffPreview {
    if (diffPreviewProcessor != null) Disposer.dispose(diffPreviewProcessor!!)
    val newProcessor = SavedPatchesDiffPreview(myProject, viewer, isInEditor, this)
    diffPreviewProcessor = newProcessor

    if (isInEditor) {
      editorTabPreview = object : SavedPatchesEditorDiffPreview(newProcessor, viewer, this@SavedPatchesChangesBrowser, focusMainUi) {
        override fun getCurrentName(): String {
          return currentPatchObject?.getDiffPreviewTitle(changeViewProcessor.currentChangeName) ?: VcsBundle.message(
            "saved.patch.editor.diff.preview.empty.title")
        }
      }
    }
    else {
      editorTabPreview = null
    }

    return newProcessor
  }

  private fun VcsTreeModelData.mapToChange(): JBIterable<Change> {
    return iterateUserObjects(SavedPatchesProvider.ChangeObject::class.java)
      .map { it.asChange() }
      .filterNotNull()
  }

  override fun getData(dataId: String): Any? {
    if (VcsDataKeys.CHANGES.`is`(dataId)) {
      val selected = VcsTreeModelData.selected(myViewer).mapToChange().toList().toTypedArray()
      if (selected.isNotEmpty()) return selected
      return VcsTreeModelData.all(myViewer).mapToChange().toList().toTypedArray()
    }
    else if (VcsDataKeys.SELECTED_CHANGES.`is`(dataId) ||
             VcsDataKeys.SELECTED_CHANGES_IN_DETAILS.`is`(dataId)) {
      return VcsTreeModelData.selected(myViewer).mapToChange().toList().toTypedArray()
    }
    else if (VcsDataKeys.CHANGES_SELECTION.`is`(dataId)) {
      return VcsTreeModelData.getListSelectionOrAll(myViewer).map { (it as? SavedPatchesProvider.ChangeObject)?.asChange() }
    }
    else if (VcsDataKeys.CHANGE_LEAD_SELECTION.`is`(dataId)) {
      return VcsTreeModelData.exactlySelected(myViewer).mapToChange().toList().toTypedArray()
    }
    else if (CommonDataKeys.VIRTUAL_FILE_ARRAY.`is`(dataId)) {
      return VcsTreeModelData.selected(myViewer).iterateUserObjects(SavedPatchesProvider.ChangeObject::class.java)
        .map { it.filePath.virtualFile }
        .filterNotNull()
        .toList().toTypedArray()
    }
    else if (VcsDataKeys.IO_FILE_ARRAY.`is`(dataId)) {
      return VcsTreeModelData.selected(myViewer).iterateUserObjects(SavedPatchesProvider.ChangeObject::class.java)
        .map { it.filePath.ioFile }
        .toList().toTypedArray()
    }
    else if (CommonDataKeys.NAVIGATABLE_ARRAY.`is`(dataId)) {
      val virtualFiles = VcsTreeModelData.selected(myViewer).iterateUserObjects(SavedPatchesProvider.ChangeObject::class.java)
        .map { it.filePath.virtualFile }
        .filterNotNull()
      return ChangesUtil.getNavigatableArray(myProject, virtualFiles)
    }
    else if (SavedPatchesUi.SAVED_PATCH_SELECTED_CHANGES.`is`(dataId)) {
      return VcsTreeModelData.selected(myViewer).iterateUserObjects(SavedPatchesProvider.ChangeObject::class.java)
    }
    else if (SavedPatchesUi.SAVED_PATCH_CHANGES.`is`(dataId)) {
      return VcsTreeModelData.all(myViewer).iterateUserObjects(SavedPatchesProvider.ChangeObject::class.java)
    }
    return super.getData(dataId)
  }

  override fun dispose() {
    shutdown()
  }

  private class ChangeObjectNode(change: SavedPatchesProvider.ChangeObject) :
    AbstractChangesBrowserFilePathNode<SavedPatchesProvider.ChangeObject>(change, change.fileStatus) {
    override fun filePath(userObject: SavedPatchesProvider.ChangeObject): FilePath = userObject.filePath
    override fun originPath(userObject: SavedPatchesProvider.ChangeObject): FilePath? = userObject.originalFilePath
  }
}