// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.platform.debugger.impl.backend.hotswap.BackendXDebuggerHotSwapApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorApi
import com.intellij.xdebugger.impl.rpc.XDebuggerManagerApi
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi
import com.intellij.xdebugger.impl.rpc.XDebuggerHotSwapApi
import com.intellij.xdebugger.impl.rpc.XDebuggerValueLookupHintsRemoteApi
import fleet.rpc.remoteApiDescriptor

private class BackendXDebuggerRemoteApiProviders : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<XDebuggerValueLookupHintsRemoteApi>()) {
      BackendXDebuggerValueLookupHintsRemoteApi()
    }
    remoteApi(remoteApiDescriptor<XDebuggerEvaluatorApi>()) {
      BackendXDebuggerEvaluatorApi()
    }
    remoteApi(remoteApiDescriptor<XDebuggerManagerApi>()) {
      BackendXDebuggerManagerApi()
    }
    remoteApi(remoteApiDescriptor<XDebugSessionApi>()) {
      BackendXDebugSessionApi()
    }
    remoteApi(remoteApiDescriptor<XDebuggerHotSwapApi>()) {
      BackendXDebuggerHotSwapApi()
    }
  }
}