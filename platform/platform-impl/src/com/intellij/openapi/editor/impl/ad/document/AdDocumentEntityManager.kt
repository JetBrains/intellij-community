// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.document

import com.intellij.openapi.application.isRhizomeAdEnabled
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.impl.ad.util.AsyncEntityHandle
import com.intellij.openapi.editor.impl.ad.util.AsyncEntityService
import com.intellij.openapi.editor.impl.ad.util.EntityCleanService
import com.intellij.openapi.fileEditor.impl.FileDocumentBindingListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.pasta.common.DocumentEntity
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


@Experimental
@Service(Level.APP)
internal class AdDocumentEntityManager() {

  companion object {
    fun getInstance(): AdDocumentEntityManager = service()

    private val DOC_ENTITY_HANDLE_KEY: Key<AsyncEntityHandle<DocumentEntity>> = Key("AD_DOC_ENTITY_KEY")
  }

  // guard DOC_ENTITY_HANDLE_KEY
  private val lock = ReentrantLock()

  suspend fun getDocEntity(document: Document): DocumentEntity? {
    return getDocHandle(document)?.entity()
  }

  fun getDocEntityRunBlocking(document: Document): DocumentEntity? {
    return getDocHandle(document)?.entityRunBlocking()
  }

  fun bindDocEntity(document: Document, oldFile: VirtualFile?, file: VirtualFile?) {
    if (isEnabled() && document is DocumentEx && oldFile == null && file != null) { // TODO: listen file reload
      val provider = AdEntityProvider.getInstance()
      val docUid = provider.getDocEntityUid(document)
      if (docUid != null) {
        lock.withLock { // ensure createEntity is called only once
          if (document.getUserData(DOC_ENTITY_HANDLE_KEY) == null) {
            val documentName = document.toString()
            val handle = AsyncEntityService.getInstance().createHandle(documentName) {
              provider.createDocEntity(docUid, document)
            }
            EntityCleanService.getInstance().registerEntity(document, documentName) {
              val entity = handle.entity()
              provider.deleteDocEntity(entity)
            }
            document.putUserData(DOC_ENTITY_HANDLE_KEY, handle)
          }
        }
      }
    }
  }

  private fun getDocHandle(document: Document): AsyncEntityHandle<DocumentEntity>? {
    if (isEnabled() && document is DocumentEx) {
      return lock.withLock {
        document.getUserData(DOC_ENTITY_HANDLE_KEY)
      }
    }
    return null
  }

  private fun isEnabled(): Boolean {
    return isRhizomeAdEnabled
  }
}

private class MyFileDocumentBindingListener : FileDocumentBindingListener {
  override fun fileDocumentBindingChanged(document: Document, oldFile: VirtualFile?, file: VirtualFile?) {
    AdDocumentEntityManager.getInstance().bindDocEntity(document, oldFile, file)
  }
}
