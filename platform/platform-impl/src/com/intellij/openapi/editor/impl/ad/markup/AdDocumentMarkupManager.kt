// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.markup

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupListener
import com.intellij.openapi.editor.impl.ad.AdTheManager
import com.intellij.openapi.editor.impl.ad.document.AdEntityProvider
import com.intellij.openapi.editor.impl.ad.isRhizomeAdRebornEnabled
import com.intellij.openapi.editor.impl.ad.util.AsyncEntityHandle
import com.intellij.openapi.editor.impl.ad.util.AsyncEntityService
import com.intellij.openapi.editor.impl.ad.util.EntityCleanService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.project.projectIdOrNull
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.rd.util.assert
import fleet.util.UID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


interface AdDocumentMarkupManager {

  companion object {
    fun getInstance(): AdDocumentMarkupManager = service<AdDocumentMarkupManagerImpl>()
  }

  suspend fun getMarkupEntity(markupModel: MarkupModelEx): AdMarkupEntity?

  @RequiresEdt
  fun getMarkupEntityRunBlocking(markupModel: MarkupModelEx): AdMarkupEntity?
}


@Experimental
@Service(Level.APP)
private class AdDocumentMarkupManagerImpl(private val coroutineScope: CoroutineScope): AdDocumentMarkupManager {

  companion object {
    private val MARKUP_ENTITY_HANDLE_KEY: Key<AsyncEntityHandle<AdMarkupEntity>> = Key.create("AD_MARKUP_ENTITY_HANDLE_KEY")

    private fun getInstance(): AdDocumentMarkupManagerImpl {
      return AdDocumentMarkupManager.getInstance() as AdDocumentMarkupManagerImpl
    }
  }

  // MARKUP_ENTITY_HANDLE_KEY guard
  private val lock = ReentrantLock()

  override suspend fun getMarkupEntity(markupModel: MarkupModelEx): AdMarkupEntity? {
    return getMarkupHandle(markupModel)?.entity()
  }

  override fun getMarkupEntityRunBlocking(markupModel: MarkupModelEx): AdMarkupEntity? {
    return getMarkupHandle(markupModel)?.entityRunBlocking()
  }

  private fun createDocMarkupEntity(project: Project?, markupModel: MarkupModelEx) {
    if (isEnabled()) {
      val entityProvider = AdEntityProvider.getInstance()
      val debugName = markupModel.toString()
      lock.withLock {
        assert(markupModel.getUserData(MARKUP_ENTITY_HANDLE_KEY) == null) {
          "unexpected existence of document markup entity already exists $debugName"
        }
        val docMarkupUid = getDocMarkupUid(project, markupModel, entityProvider)
        if (docMarkupUid != null) {
          val handle = AsyncEntityService.getInstance().createHandle(debugName) {
            entityProvider.createDocMarkupEntity(docMarkupUid, markupModel)
          }
          EntityCleanService.getInstance().registerEntity(markupModel, debugName) {
            val entity = handle.entity()
            entityProvider.deleteDocMarkupEntity(entity)
          }
          markupModel.putUserData(MARKUP_ENTITY_HANDLE_KEY, handle)
        }
      }
    }
  }

  private fun deleteDocMarkupEntity(markupModel: MarkupModelEx) {
    if (isEnabled()) {
      val provider = AdEntityProvider.getInstance()
      val debugName = markupModel.document.toString()
      lock.withLock {
        val handle = markupModel.getUserData(MARKUP_ENTITY_HANDLE_KEY)
        checkNotNull(handle) {
          // TODO: investigate why happens
          "document markup entity not found $debugName"
        }
        coroutineScope.async(AdTheManager.AD_DISPATCHER) {
          val entity = handle.entity()
          provider.deleteDocMarkupEntity(entity)
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
    return if (isEnabled()) {
      lock.withLock {
        markupModel.getUserData(MARKUP_ENTITY_HANDLE_KEY)
      }
    } else {
      null
    }
  }

  private fun isEnabled(): Boolean {
    return isRhizomeAdRebornEnabled
  }

  class AdDocumentMarkupListener : DocumentMarkupListener {
    override fun markupModelCreated(project: Project?, markupModel: MarkupModelEx) {
      getInstance().createDocMarkupEntity(project, markupModel)
    }
    override fun markupModelDisposed(project: Project?, markupModel: MarkupModelEx) {
      getInstance().deleteDocMarkupEntity(markupModel)
    }
  }
}
