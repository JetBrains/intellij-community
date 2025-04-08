// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Used only for Java code, since MutableStateFlow function cannot be called there.
internal fun <T> createMutableStateFlow(initialValue: T): MutableStateFlow<T> {
  return MutableStateFlow(initialValue)
}

@Service(Service.Level.PROJECT)
internal class XDebugSessionSelectionService(project: Project, scope: CoroutineScope) {
  init {
    scope.launch {
      XDebugManagerProxy.getInstance().getCurrentSessionFlow(project).collectLatest { currentSession ->
        // switch to EDT, so select can execute immediately (it uses invokeLaterIfNeeded)
        withContext(Dispatchers.EDT) {
          currentSession?.sessionTab?.select()
        }
      }
    }
  }

  companion object {
    @JvmStatic
    fun startCurrentSessionListening(project: Project) {
      project.service<XDebugSessionSelectionService>()
    }
  }
}