// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.document

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.impl.ad.isRhizomeAdRebornEnabled
import com.intellij.openapi.editor.impl.ad.util.AsyncEntityHandle
import com.intellij.openapi.editor.impl.ad.util.AsyncEntityService
import com.intellij.openapi.editor.impl.ad.util.EntityCleanService
import com.intellij.openapi.fileEditor.impl.FileDocumentBindingListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.pasta.common.DocumentEntity
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


interface AdDocumentManager {

  companion object {
    fun getInstance(): AdDocumentManager = service<AdDocumentManagerImpl>()
  }

  suspend fun getDocEntity(document: DocumentEx): DocumentEntity?

  @RequiresEdt
  fun getDocEntityRunBlocking(document: DocumentEx): DocumentEntity?
}


@Service(Level.APP)
private class AdDocumentManagerImpl : AdDocumentManager {

  companion object {
    private val DOC_ENTITY_HANDLE_KEY: Key<AsyncEntityHandle<DocumentEntity>> = Key("AD_DOCUMENT_ENTITY_KEY")

    private fun getInstance(): AdDocumentManagerImpl {
      return AdDocumentManager.getInstance() as AdDocumentManagerImpl
    }
  }

  // DOC_ENTITY_HANDLE_KEY guard
  private val lock = ReentrantLock()

  override suspend fun getDocEntity(document: DocumentEx): DocumentEntity? {
    return getDocHandle(document)?.entity()
  }

  override fun getDocEntityRunBlocking(document: DocumentEx): DocumentEntity? {
    return getDocHandle(document)?.entityRunBlocking()
  }

  private fun bindDocEntity(document: Document, oldFile: VirtualFile?, file: VirtualFile?) {
    if (isEnabled() && document is DocumentEx && oldFile == null && file != null) { // TODO: listen file reload
      val entityProvider = AdEntityProvider.getInstance()
      val docUid = entityProvider.getDocEntityUid(document)
      if (docUid != null) {
        lock.withLock {
          if (document.getUserData(DOC_ENTITY_HANDLE_KEY) == null) {
            val documentName = document.toString()
            val handle = AsyncEntityService.getInstance().createHandle(documentName) {
              entityProvider.createDocEntity(docUid, document)
            }
            EntityCleanService.getInstance().registerEntity(document, documentName) {
              val entity = handle.entity()
              entityProvider.deleteDocEntity(entity)
            }
            document.putUserData(DOC_ENTITY_HANDLE_KEY, handle)
          }
        }
      }
    }
  }

  private fun getDocHandle(document: DocumentEx): AsyncEntityHandle<DocumentEntity>? {
    return if (isEnabled()) {
      lock.withLock {
        document.getUserData(DOC_ENTITY_HANDLE_KEY)
      }
    } else {
      null
    }
  }

  private fun isEnabled(): Boolean {
    return isRhizomeAdRebornEnabled
  }

  class AdFileDocumentBindingListener : FileDocumentBindingListener {
    override fun fileDocumentBindingChanged(document: Document, oldFile: VirtualFile?, file: VirtualFile?) {
      getInstance().bindDocEntity(document, oldFile, file)
    }
  }
}
