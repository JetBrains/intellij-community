// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick.common

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.debugger.impl.rpc.ValueHintEvent
import com.intellij.platform.debugger.impl.rpc.XDebuggerValueLookupHintsRemoteApi
import com.intellij.platform.project.projectId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class ValueLookupManagerProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    // in production mode we can use coroutineScope { ... } here, since `execute` will be called canceled on project close,
    // but it won't happen in tests mode, so we need to have a separate service for the subscription.
    project.serviceAsync<ValueLookupManagerSubscriptionService>()
  }
}

@Service(Service.Level.PROJECT)
private class ValueLookupManagerSubscriptionService(project: Project, cs: CoroutineScope) {
  init {
    cs.launch {
      XDebuggerValueLookupHintsRemoteApi.getInstance().getManagerEventsFlow(project.projectId()).collect { event ->
        when (event) {
          ValueHintEvent.HideHint -> {
            withContext(Dispatchers.EDT) {
              ValueLookupManager.getInstance(project).hideHint()
            }
          }
          ValueHintEvent.StartListening -> {
            withContext(Dispatchers.EDT) {
              ValueLookupManager.getInstance(project).startListening()
            }
          }
        }
      }
    }
  }
}