// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.markup

import com.intellij.openapi.application.isRhizomeAdEnabled
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.ad.AdTheManager
import com.intellij.openapi.editor.impl.ad.document.AdEntityProvider
import com.intellij.openapi.editor.impl.ad.util.AsyncEntityHandle
import com.intellij.openapi.editor.impl.ad.util.AsyncEntityService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.project.projectIdOrNull
import fleet.util.UID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.*
import kotlin.concurrent.withLock


@Experimental
@Service(Level.PROJECT)
internal class AdMarkupModelManager(private val project: Project, private val coroutineScope: CoroutineScope) {

  companion object {
    fun getInstance(project: Project): AdMarkupModelManager = project.service()

    private val MARKUP_ENTITY_HANDLE_KEY: Key<AsyncEntityHandle<AdMarkupEntity>> = Key.create("AD_MARKUP_ENTITY_HANDLE_KEY")
  }

  private val lock = java.util.concurrent.locks.ReentrantLock()

  suspend fun getMarkupEntity(markupModel: MarkupModelEx): AdMarkupEntity? {
    return getMarkupHandle(markupModel)?.entity()
  }

  fun getMarkupEntityRunBlocking(markupModel: MarkupModelEx): AdMarkupEntity? {
    return getMarkupHandle(markupModel)?.entityRunBlocking()
  }

  fun bindMarkupModelEntity(markupModel: MarkupModelEx) {
    if (isEnabled()) {
      val provider = AdEntityProvider.getInstance()
      val markupUid = getMarkupEntityUid(project, markupModel, provider)
      if (markupUid != null) {
        lock.withLock {
          val existingHandle = markupModel.getUserData(MARKUP_ENTITY_HANDLE_KEY)
          val nextHandle = if (existingHandle != null) {
            existingHandle.incRefCount()
          } else {
            val debugName = markupModel.document.toString()
            AsyncEntityService.getInstance().createHandle(debugName) {
              provider.createMarkupEntity(markupUid, project, markupModel)
            }
          }
          markupModel.putUserData(MARKUP_ENTITY_HANDLE_KEY, nextHandle)
        }
      }
    }
  }

  fun releaseMarkupModelEntity(markupModel: MarkupModelEx) {
    if (isEnabled()) {
      val provider = AdEntityProvider.getInstance()
      lock.withLock {
        val existingHandle = markupModel.getUserData(MARKUP_ENTITY_HANDLE_KEY)
        checkNotNull(existingHandle) { "handle not found" }
        val nextHandle = existingHandle.decRefCount()
        if (nextHandle == null) {
          coroutineScope.async(AdTheManager.AD_DISPATCHER) {
            val entity = existingHandle.entity()
            provider.deleteMarkupEntity(entity)
          }
        }
        markupModel.putUserData(MARKUP_ENTITY_HANDLE_KEY, nextHandle)
      }
    }
  }

  private fun getMarkupEntityUid(project: Project, markupModel: MarkupModelEx, provider: AdEntityProvider): UID? {
    val projectId = project.projectIdOrNull()?.serializeToString()
    val docId = (markupModel.document as? DocumentEx)?.let { provider.getDocEntityUid(it) }
    if (projectId != null && docId != null) {
      val markupId = UUID.nameUUIDFromBytes("$projectId$docId".toByteArray())
      return UID.fromString(markupId.toString())
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
