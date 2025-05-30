// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.APP)
class OpenFileChooserService(val coroutineScope: CoroutineScope) {

  fun chooseDirectory(project: Project, initialDirectory: String, onResult: (@NlsSafe String?) -> Any?) {
    coroutineScope.launch {
      val result = OpenFileChooserApi.getInstance().chooseDirectory(project.projectId(), initialDirectory)
      onResult(result)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): OpenFileChooserService = service()
  }
}