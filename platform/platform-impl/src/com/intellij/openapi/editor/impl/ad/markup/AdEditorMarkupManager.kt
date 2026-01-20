// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.markup

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.ad.AdTheManager
import com.intellij.openapi.editor.impl.ad.document.AdEntityProvider
import com.intellij.openapi.editor.impl.ad.isRhizomeAdRebornEnabled
import com.intellij.openapi.editor.impl.ad.util.AsyncEntityService
import com.intellij.util.concurrency.annotations.RequiresEdt
import fleet.util.UID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Experimental


@Experimental
@Service(Level.APP)
internal class AdEditorMarkupManager(private val coroutineScope: CoroutineScope) {

  companion object {
    fun getInstance(): AdEditorMarkupManager = service()
  }

  @RequiresEdt
  fun createEditorMarkupEntityRunBlocking(uid: UID, markupModel: MarkupModelEx): AdMarkupEntity? {
    if (isEnabled()) {
      val entityProvider = AdEntityProvider.getInstance()
      val debugName = markupModel.document.toString()
      val handle = AsyncEntityService.getInstance().createHandle(debugName) {
        entityProvider.createDocMarkupEntity(uid, markupModel)
      }
      return handle.entityRunBlocking()
    }
    return null
  }

  fun deleteEditorMarkupEntityRunBlocking(markupEntity: AdMarkupEntity) {
    if (isEnabled()) {
      val entityProvider = AdEntityProvider.getInstance()
      coroutineScope.launch(AdTheManager.AD_DISPATCHER) {
        entityProvider.deleteDocMarkupEntity(markupEntity)
      }
    }
  }

  private fun isEnabled(): Boolean {
    return isRhizomeAdRebornEnabled
  }
}
