// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.shared

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class RecentFilesCoroutineScopeProvider(val coroutineScope: CoroutineScope) {
  companion object {
    fun getInstance(project: Project): RecentFilesCoroutineScopeProvider {
      return project.service<RecentFilesCoroutineScopeProvider>()
    }

    suspend fun getInstanceAsync(project: Project): RecentFilesCoroutineScopeProvider {
      return project.serviceAsync<RecentFilesCoroutineScopeProvider>()
    }
  }
}