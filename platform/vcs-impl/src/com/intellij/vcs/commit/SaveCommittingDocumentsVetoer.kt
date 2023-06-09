// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.getQuestionIcon
import com.intellij.openapi.ui.Messages.showOkCancelDialog
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vfs.VirtualFile

private sealed class SaveState
private object SaveDenied : SaveState()
private class RequireConfirmSave(val project: Project) : SaveState()

private val SAVE_STATE_KEY = Key<SaveState>("Vcs.Commit.SaveState")

private fun getSaveState(file: VirtualFile): SaveState? = file.getUserData(SAVE_STATE_KEY)
private fun setSaveState(file: VirtualFile, state: SaveState?) = file.putUserData(SAVE_STATE_KEY, state)
private fun replaceSaveState(file: VirtualFile, expectedOldState: SaveState?, newState: SaveState?) =
  (file as UserDataHolderEx).replace(SAVE_STATE_KEY, expectedOldState, newState)

internal class SaveCommittingDocumentsVetoer : FileDocumentSynchronizationVetoer(), FileDocumentManagerListener {
  override fun beforeAllDocumentsSaving() {
    val fileDocumentManager = FileDocumentManager.getInstance()
    val unsavedFiles = fileDocumentManager.unsavedDocuments
      .mapNotNull { document -> fileDocumentManager.getFile(document) }

    val protectedFiles = buildMap {
      for (file in unsavedFiles) {
        val state = getSaveState(file) as? RequireConfirmSave ?: continue
        put(file, state)
      }
    }
    if (protectedFiles.isEmpty()) return

    val project = protectedFiles.values.first().project
    val saveConfirmed = confirmSave(project, protectedFiles.keys)

    // use `replace` as commit could already be finished
    val newState = if (saveConfirmed) null else SaveDenied
    for ((file, oldState) in protectedFiles) {
      replaceSaveState(file, oldState, newState)
    }
  }

  override fun maySaveDocument(document: Document, isSaveExplicit: Boolean): Boolean {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return true
    val saveState = getSaveState(file) ?: return true
    when (saveState) {
      SaveDenied -> return false
      is RequireConfirmSave -> return confirmSave(saveState.project, listOf(file))
    }
  }
}

fun vetoDocumentSaving(project: Project, changes: Collection<Change>, block: () -> Unit) {
  vetoDocumentSavingForPaths(project, ChangesUtil.getPaths(changes), block)
}

fun vetoDocumentSavingForPaths(project: Project, filePaths: Collection<FilePath>, block: () -> Unit) {
  val files = filePaths.mapNotNull { it.virtualFile }

  val confirmSaveState = RequireConfirmSave(project)
  files.forEach { setSaveState(it, confirmSaveState) }
  try {
    block()
  }
  finally {
    files.forEach { setSaveState(it, null) }
  }
}

private fun confirmSave(project: Project, files: Collection<VirtualFile>): Boolean {
  val text = message("save.committing.files.confirmation.text", files.size, files.joinToString("\n") { it.presentableUrl })

  return Messages.OK == showOkCancelDialog(
    project,
    text,
    message("save.committing.files.confirmation.title"),
    message("save.committing.files.confirmation.ok"),
    message("save.committing.files.confirmation.cancel"),
    getQuestionIcon()
  )
}