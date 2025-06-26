// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException

private val LOG = logger<OpenFileChooserService>()

@ApiStatus.Internal
@Service(Service.Level.APP)
class OpenFileChooserService(val coroutineScope: CoroutineScope) {
  private var chooseDirectoryJob: Job? = null

  fun chooseDirectory(project: Project, initialDirectory: String, onResult: (@NlsSafe String?) -> Any?) {
    chooseDirectoryJob = coroutineScope.launch {
      try {
        val result = OpenFileChooserApi.getInstance().chooseDirectory(project.projectId(), initialDirectory)
        onResult(result)
      }
      catch (e: CancellationException) {
        onResult(null)
        throw e  // Propagate cancellation
      }
      catch (e: Exception) {
        LOG.warn("Directory selection failed", e)
        chooseDirectoryJob?.cancel()
        onResult(null)
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): OpenFileChooserService = service()
  }
}