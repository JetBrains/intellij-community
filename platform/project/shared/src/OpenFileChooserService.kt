// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.cancelOnDispose
import fleet.rpc.client.RpcTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<OpenFileChooserService>()

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class OpenFileChooserService(private val project: Project, val coroutineScope: CoroutineScope) {
  private var chooseDirectoryJob: Job? = null

  fun chooseDirectory(initialDirectory: String, onResult: (@NlsSafe String?) -> Any?) {
    chooseDirectoryJob = coroutineScope.launch {
      val deferred = try {
         OpenFileChooserApi.getInstance().chooseDirectory(project.projectId(), initialDirectory)
      }
      catch (e: RpcTimeoutException) {
        LOG.warn("Directory selection failed", e)
        null
      }
      deferred?.cancelOnDispose(project)
      deferred?.invokeOnCompletion { cause ->
        if (cause != null) {
          onResult(null)
        }
      }
      onResult(deferred?.await())
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): OpenFileChooserService = project.service()
  }
}