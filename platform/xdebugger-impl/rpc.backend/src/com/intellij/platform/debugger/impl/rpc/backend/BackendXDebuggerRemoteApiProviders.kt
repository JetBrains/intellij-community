// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc.backend

import com.intellij.openapi.components.service
import com.intellij.platform.debugger.impl.rpc.XBreakpointApi
import com.intellij.platform.debugger.impl.rpc.XBreakpointTypeApi
import com.intellij.platform.debugger.impl.rpc.XDebugSessionApi
import com.intellij.platform.debugger.impl.rpc.XDebugSessionTabApi
import com.intellij.platform.debugger.impl.rpc.XDebuggerEvaluatorApi
import com.intellij.platform.debugger.impl.rpc.XDebuggerHotSwapApi
import com.intellij.platform.debugger.impl.rpc.XDebuggerManagerApi
import com.intellij.platform.debugger.impl.rpc.XDebuggerNavigationApi
import com.intellij.platform.debugger.impl.rpc.XDebuggerValueLookupHintsRemoteApi
import com.intellij.platform.debugger.impl.rpc.XDebuggerValueMarkupApi
import com.intellij.platform.debugger.impl.rpc.XDebuggerValueModifierApi
import com.intellij.platform.debugger.impl.rpc.XDependentBreakpointManagerApi
import com.intellij.platform.debugger.impl.rpc.XExecutionStackApi
import com.intellij.platform.debugger.impl.rpc.XMixedModeApi
import com.intellij.platform.debugger.impl.rpc.XValueApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

/**
 * Serves the debugger APIs to a frontend.
 *
 * The implementations are application services of `intellij.platform.debugger.impl.backend`.
 * A light IDE without a backend reaches the same services through `LiteRemoteApiProviderService.awaitWithOptionalLocalFallback`.
 */
internal class BackendXDebuggerRemoteApiProviders : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<XDebuggerValueLookupHintsRemoteApi>()) { service<XDebuggerValueLookupHintsRemoteApi>() }
    remoteApi(remoteApiDescriptor<XDebuggerEvaluatorApi>()) { service<XDebuggerEvaluatorApi>() }
    remoteApi(remoteApiDescriptor<XDebuggerManagerApi>()) { service<XDebuggerManagerApi>() }
    remoteApi(remoteApiDescriptor<XDebugSessionApi>()) { service<XDebugSessionApi>() }
    remoteApi(remoteApiDescriptor<XDebugSessionTabApi>()) { service<XDebugSessionTabApi>() }
    remoteApi(remoteApiDescriptor<XDebuggerHotSwapApi>()) { service<XDebuggerHotSwapApi>() }
    remoteApi(remoteApiDescriptor<XDebuggerValueModifierApi>()) { service<XDebuggerValueModifierApi>() }
    remoteApi(remoteApiDescriptor<XDebuggerValueMarkupApi>()) { service<XDebuggerValueMarkupApi>() }
    remoteApi(remoteApiDescriptor<XDebuggerNavigationApi>()) { service<XDebuggerNavigationApi>() }
    remoteApi(remoteApiDescriptor<XValueApi>()) { service<XValueApi>() }
    remoteApi(remoteApiDescriptor<XExecutionStackApi>()) { service<XExecutionStackApi>() }
    remoteApi(remoteApiDescriptor<XBreakpointApi>()) { service<XBreakpointApi>() }
    remoteApi(remoteApiDescriptor<XBreakpointTypeApi>()) { service<XBreakpointTypeApi>() }
    remoteApi(remoteApiDescriptor<XDependentBreakpointManagerApi>()) { service<XDependentBreakpointManagerApi>() }
    remoteApi(remoteApiDescriptor<XMixedModeApi>()) { service<XMixedModeApi>() }
  }
}
