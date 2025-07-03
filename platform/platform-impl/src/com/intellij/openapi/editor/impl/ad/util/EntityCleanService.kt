// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.impl.ad.AdTheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Experimental
import java.lang.ref.Cleaner


private val CLEANER by lazy { Cleaner.create() } // TODO: shared cleaner for the whole IJ platform

@Experimental
@Service(Level.APP)
internal class EntityCleanService(private val coroutineScope: CoroutineScope) {

  companion object {
    fun getInstance(): EntityCleanService = service()
  }

  fun registerEntity(reachabilityMonitor: Any, debugName: String, deleteEntity: suspend () -> Unit): Cleaner.Cleanable {
    return CLEANER.register(
      reachabilityMonitor,
      Runnable { deleteUnreachableEntity(debugName, deleteEntity) },
    )
  }

  // do not capture the monitor!
  private fun deleteUnreachableEntity(debugName: String, deleteEntity: suspend () -> Unit) {
    coroutineScope.launch(AdTheManager.AD_DISPATCHER) {
      AdTheManager.LOG.debug {
        "$debugName is unreachable, removing corresponding entity"
      }
      deleteEntity.invoke() // TODO: why not invoked for editor's document?
    }
  }
}
