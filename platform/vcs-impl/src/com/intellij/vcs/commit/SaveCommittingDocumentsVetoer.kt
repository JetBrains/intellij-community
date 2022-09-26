// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.application.runReadAction
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

private sealed class SaveState
private object SaveDenied : SaveState()
private class ConfirmSave(val project: Project) : SaveState()

private val SAVE_STATE_KEY = Key<SaveState>("Vcs.Commit.SaveState")

private fun getSaveState(document: Document): SaveState? = document.getUserData(SAVE_STATE_KEY)

private fun setSaveState(documents: Collection<Document>, state: SaveState?) =
  documents.forEach { it.putUserData(SAVE_STATE_KEY, state) }

private fun replaceSaveState(documents: Map<Document, SaveState?>, newState: SaveState?) =
  documents.forEach { (document, oldState) -> (document as UserDataHolderEx).replace(SAVE_STATE_KEY, oldState, newState) }

internal class SaveCommittingDocumentsVetoer : FileDocumentSynchronizationVetoer(), FileDocumentManagerListener {
  override fun beforeAllDocumentsSaving() {
    val confirmSaveDocuments =
      FileDocumentManager.getInstance().unsavedDocuments
        .associateBy({ it }, { getSaveState(it) })
        .filterValues { it is ConfirmSave }
        .mapValues { it.value as ConfirmSave }
    if (confirmSaveDocuments.isEmpty()) return

    val project = confirmSaveDocuments.values.first().project
    val newSaveState = if (confirmSave(project, confirmSaveDocuments.keys)) null else SaveDenied
    replaceSaveState(confirmSaveDocuments, newSaveState) // use `replace` as commit could already be completed
  }

  override fun maySaveDocument(document: Document, isSaveExplicit: Boolean): Boolean =
    when (val saveState = getSaveState(document)) {
      SaveDenied -> false
      is ConfirmSave -> confirmSave(saveState.project, listOf(document))
      null -> true
    }
}

fun vetoDocumentSaving(project: Project, changes: Collection<Change>, block: () -> Unit) {
  vetoDocumentSavingForPaths(project, ChangesUtil.getPaths(changes), block)
}

fun vetoDocumentSavingForPaths(project: Project, filePaths: Collection<FilePath>, block: () -> Unit) {
  val confirmSaveState = ConfirmSave(project)
  val documents = runReadAction { getDocuments(filePaths).also { setSaveState(it, confirmSaveState) } }
  try {
    block()
  }
  finally {
    runReadAction { setSaveState(documents, null) }
  }
}

private fun getDocuments(filePaths: Iterable<FilePath>): List<Document> =
  filePaths
    .mapNotNull { it.virtualFile }
    .filterNot { it.fileType.isBinary }
    .mapNotNull { FileDocumentManager.getInstance().getDocument(it) }
    .toList()

private fun confirmSave(project: Project, documents: Collection<Document>): Boolean {
  val files = documents.mapNotNull { FileDocumentManager.getInstance().getFile(it) }
  val text = message("save.committing.files.confirmation.text", documents.size, files.joinToString("\n") { it.presentableUrl })

  return Messages.OK == showOkCancelDialog(
    project,
    text,
    message("save.committing.files.confirmation.title"),
    message("save.committing.files.confirmation.ok"),
    message("save.committing.files.confirmation.cancel"),
    getQuestionIcon()
  )
}