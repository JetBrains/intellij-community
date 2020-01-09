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
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.vcs.VcsBundle.message

private val SAVE_DENIED = Any()

private fun getDocumentsBeingCommitted(): Map<Document, Project> {
  val documentsToWarn = mutableMapOf<Document, Project>()
  for (unsavedDocument in FileDocumentManager.getInstance().unsavedDocuments) {
    val data = unsavedDocument.getUserData(AbstractCommitter.DOCUMENT_BEING_COMMITTED_KEY)
    if (data is Project) {
      documentsToWarn[unsavedDocument] = data
    }
  }
  return documentsToWarn
}

private fun updateSaveability(documentsToWarn: Map<Document, Project>, allowSave: Boolean) {
  val newValue = if (allowSave) null else SAVE_DENIED
  for (document in documentsToWarn.keys) {
    val oldData = documentsToWarn[document]
    //the committing thread could have finished already and file is not being committed anymore
    (document as UserDataHolderEx).replace(AbstractCommitter.DOCUMENT_BEING_COMMITTED_KEY, oldData, newValue)
  }
}

internal class SaveCommittingDocumentsVetoer : FileDocumentSynchronizationVetoer(), FileDocumentManagerListener {
  override fun beforeAllDocumentsSaving() {
    val documentsToWarn = getDocumentsBeingCommitted()
    if (documentsToWarn.isEmpty()) return

    val project = documentsToWarn.values.first()
    val allowSave = confirmSave(project, documentsToWarn.keys)
    updateSaveability(documentsToWarn, allowSave)
  }

  override fun maySaveDocument(document: Document, isSaveExplicit: Boolean): Boolean =
    when (val beingCommitted = document.getUserData(AbstractCommitter.DOCUMENT_BEING_COMMITTED_KEY)) {
      SAVE_DENIED -> false
      is Project -> confirmSave(beingCommitted, listOf(document))
      else -> true
    }
}

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