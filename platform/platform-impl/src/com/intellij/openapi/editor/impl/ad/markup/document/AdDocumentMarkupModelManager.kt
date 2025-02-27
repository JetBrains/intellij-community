// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.markup.document

import com.intellij.openapi.application.isRhizomeAdEnabled
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModelListener
import com.intellij.openapi.editor.impl.ad.AdTheManager
import com.intellij.openapi.editor.impl.ad.document.AdEntityProvider
import com.intellij.openapi.editor.impl.ad.markup.AdMarkupEntity
import com.intellij.openapi.editor.impl.ad.markup.document.AdDocumentMarkupModelManager.Companion.getInstance
import com.intellij.openapi.editor.impl.ad.util.AsyncEntityHandle
import com.intellij.openapi.editor.impl.ad.util.AsyncEntityService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.project.projectIdOrNull
import com.jetbrains.rd.util.assert
import fleet.util.UID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.UUID
import kotlin.concurrent.withLock


@Experimental
@Service(Level.APP)
internal class AdDocumentMarkupModelManager(private val coroutineScope: CoroutineScope) {

  companion object {
    fun getInstance(): AdDocumentMarkupModelManager = service()

    private val MARKUP_ENTITY_HANDLE_KEY: Key<AsyncEntityHandle<AdMarkupEntity>> = Key.create("AD_MARKUP_ENTITY_HANDLE_KEY")
  }

  private val lock = java.util.concurrent.locks.ReentrantLock()

  fun getMarkupEntityRunBlocking(markupModel: MarkupModelEx): AdMarkupEntity? {
    return getMarkupHandle(markupModel)?.entityRunBlocking()
  }

  fun createDocMarkupEntity(project: Project?, markupModel: MarkupModelEx) {
    if (isEnabled()) {
      val entityProvider = AdEntityProvider.getInstance()
      val debugName = markupModel.document.toString()
      lock.withLock {
        assert(markupModel.getUserData(MARKUP_ENTITY_HANDLE_KEY) == null) {
          "unexpected existence of document markup entity already exists $debugName"
        }
        val docMarkupUid = getDocMarkupUid(project, markupModel, entityProvider)
        if (docMarkupUid != null) {
          val handle = AsyncEntityService.getInstance().createHandle(debugName) {
            entityProvider.createMarkupEntity(docMarkupUid, markupModel)
          }
          markupModel.putUserData(MARKUP_ENTITY_HANDLE_KEY, handle)
        }
      }
    }
  }

  fun deleteDocMarkupEntity(project: Project?, markupModel: MarkupModelEx) {
    if (isEnabled()) {
      val provider = AdEntityProvider.getInstance()
      val debugName = markupModel.document.toString()
      lock.withLock {
        val handle = markupModel.getUserData(MARKUP_ENTITY_HANDLE_KEY)
        checkNotNull(handle) {
          "document markup entity not found $debugName"
        }
        coroutineScope.async(AdTheManager.AD_DISPATCHER) {
          val entity = handle.entity()
          provider.deleteMarkupEntity(entity)
        }
      }
    }
  }

  private fun getDocMarkupUid(project: Project?, markupModel: MarkupModelEx, provider: AdEntityProvider): UID? {
    val document = markupModel.document
    if (document is DocumentEx) {
      val projectIdAsString = if (project == null) {
        ""
      } else {
        // if project != null and projectIdOrNull == null then do not create doc markup entity
        project.projectIdOrNull()?.serializeToString()
      }
      if (projectIdAsString != null) {
        val docUidAsString = provider.getDocEntityUid(document)?.toString()
        if (docUidAsString != null) {
          val documentMarkupId = UUID.nameUUIDFromBytes("$projectIdAsString$docUidAsString".toByteArray())
          return UID.fromString(documentMarkupId.toString())
        }
      }
    }
    return null
  }

  private fun getMarkupHandle(markupModel: MarkupModelEx): AsyncEntityHandle<AdMarkupEntity>? {
    if (isEnabled()) {
      return lock.withLock {
        markupModel.getUserData(MARKUP_ENTITY_HANDLE_KEY)
      }
    }
    return null
  }

  private fun isEnabled(): Boolean {
    return isRhizomeAdEnabled
  }
}

private class AdDocumentMarkupModelListener : DocumentMarkupModelListener {
  override fun markupModelCreated(project: Project?, markupModel: MarkupModelEx) {
    getInstance().createDocMarkupEntity(project, markupModel)
  }

  override fun markupModelDisposed(project: Project?, markupModel: MarkupModelEx) {
    getInstance().deleteDocMarkupEntity(project, markupModel)
  }
}
