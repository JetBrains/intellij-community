// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.actions.vectorAsset

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job

@Service(Service.Level.PROJECT)
internal class ComposeResourcesCoroutineScopeService(private val coroutineScope: CoroutineScope) {
  fun childScope(name: String, parentDisposable: Disposable): CoroutineScope =
    coroutineScope.childScope(name).apply { coroutineContext.job.cancelOnDispose(parentDisposable) }

  companion object {
    fun getInstance(project: Project): ComposeResourcesCoroutineScopeService = project.service()
  }
}