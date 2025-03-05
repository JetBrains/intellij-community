// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick.common

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.project.asProjectOrNull
import com.intellij.xdebugger.impl.evaluate.XDebuggerValueLookupHideHintsRequestEntity
import com.intellij.xdebugger.impl.evaluate.XDebuggerValueLookupListeningStartedEntity
import fleet.kernel.change
import fleet.kernel.rete.collect
import fleet.kernel.rete.each
import fleet.kernel.rete.filter
import fleet.kernel.shared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun subscribeForDebuggingStart(cs: CoroutineScope, project: Project, onStartListening: () -> Unit) {
  cs.launch(Dispatchers.IO) {
    XDebuggerValueLookupListeningStartedEntity.each().filter { it.projectEntity.asProjectOrNull() === project }.collect {
      withContext(Dispatchers.EDT) {
        onStartListening()
      }
    }
  }
}

internal fun subscribeForValueHintHideRequest(cs: CoroutineScope, project: Project, onHintHidden: () -> Unit) {
  cs.launch(Dispatchers.IO) {
    XDebuggerValueLookupHideHintsRequestEntity.each().filter { it.projectEntity.asProjectOrNull() === project }.collect { entity ->
      withContext(Dispatchers.EDT) {
        onHintHidden()
      }
      // TODO: support multiple clients by clientId
      cs.launch(Dispatchers.IO) {
        change {
          shared {
            entity.delete()
          }
        }
      }
    }
  }
}