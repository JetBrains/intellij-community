// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frontend.evaluate.quick.common

import com.intellij.platform.kernel.KernelService
import com.intellij.xdebugger.impl.evaluate.XDebuggerValueLookupHideHintsRequestEntity
import com.intellij.xdebugger.impl.evaluate.XDebuggerValueLookupListeningStartedEntity
import fleet.kernel.change
import fleet.kernel.rete.collect
import fleet.kernel.rete.each
import fleet.kernel.shared
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun subscribeForDebuggingStart(cs: CoroutineScope, onStartListening: () -> Unit) {
  cs.launch(Dispatchers.Default) {
    withContext(KernelService.kernelCoroutineContext()) {
      change {
        shared {
          register(XDebuggerValueLookupListeningStartedEntity)
        }
      }
      XDebuggerValueLookupListeningStartedEntity.each().collect {
        withContext(Dispatchers.Main) {
          onStartListening()
        }
      }
    }
  }
}

internal fun subscribeForValueHintHideRequest(cs: CoroutineScope, onHintHidden: () -> Unit) {
  cs.launch(Dispatchers.Default) {
    withContext(KernelService.kernelCoroutineContext()) {
      change {
        shared {
          register(XDebuggerValueLookupHideHintsRequestEntity)
        }
      }
      XDebuggerValueLookupHideHintsRequestEntity.each().collect { entity ->
        withContext(Dispatchers.Main) {
          onHintHidden()
        }
        // TODO: support multiple clients by clientId
        cs.launch(Dispatchers.Default + KernelService.kernelCoroutineContext()) {
          change {
            shared {
              entity.delete()
            }
          }
        }
      }
    }
  }
}