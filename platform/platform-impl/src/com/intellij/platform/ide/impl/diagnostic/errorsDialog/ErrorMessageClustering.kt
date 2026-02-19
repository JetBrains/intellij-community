// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.diagnostic.errorsDialog

import com.intellij.diagnostic.IdeErrorsDialog.Companion.hashMessage
import com.intellij.diagnostic.MessagePool
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

@Service(Service.Level.APP)
internal class ErrorMessageClustering(private val coroutineScope: CoroutineScope) {
  companion object {
    fun getInstance(): ErrorMessageClustering = service<ErrorMessageClustering>()
  }

  internal fun clusterMessages(): Deferred<List<ErrorMessageCluster>> {
    return coroutineScope.async {
      val messages = MessagePool.getInstance().getFatalErrors(true, true)
      messages
        .groupBy { hashMessage(it) }
        .map { ErrorMessageCluster(it.value) }
    }
  }

}
