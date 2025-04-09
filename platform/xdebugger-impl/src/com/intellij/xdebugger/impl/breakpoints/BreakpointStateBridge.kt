// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.xdebugger.breakpoints.SuspendPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridge for accessing [BreakpointState] with reactive updates on some its states.
 */
internal class BreakpointStateBridge<S : BreakpointState>(val state: S) {
  private val isEnabledState = MutableStateFlow(state.isEnabled)
  private val suspendPolicyState = MutableStateFlow(state.suspendPolicy)
  private val logMessageState = MutableStateFlow(state.isLogMessage)
  private val logStackState = MutableStateFlow(state.isLogStack)
  private val groupState = MutableStateFlow(state.group)
  private val descriptionState = MutableStateFlow(state.description)

  fun isEnabled(): Boolean {
    return state.isEnabled
  }

  fun setEnabled(enabled: Boolean) {
    isEnabledState.value = enabled
    state.isEnabled = enabled
  }

  fun isEnabledFlow(): StateFlow<Boolean> = isEnabledState.asStateFlow()

  fun getSuspendPolicy(): SuspendPolicy {
    return state.suspendPolicy
  }

  fun setSuspendPolicy(suspendPolicy: SuspendPolicy) {
    suspendPolicyState.value = suspendPolicy
    state.suspendPolicy = suspendPolicy
  }

  fun suspendPolicyFlow(): StateFlow<SuspendPolicy> = suspendPolicyState.asStateFlow()

  fun isLogMessage(): Boolean {
    return state.isLogMessage
  }

  fun setLogMessage(logMessage: Boolean) {
    logMessageState.value = logMessage
    state.isLogMessage = logMessage
  }

  fun logMessageFlow(): StateFlow<Boolean> = logMessageState.asStateFlow()

  fun isLogStack(): Boolean {
    return state.isLogStack
  }

  fun setLogStack(logStack: Boolean) {
    logStackState.value = logStack
    state.isLogStack = logStack
  }

  fun logStackFlow(): StateFlow<Boolean> = logStackState.asStateFlow()

  fun getGroup(): String? {
    return state.group
  }

  fun setGroup(group: String?) {
    groupState.value = group
    state.group = group
  }

  fun groupFlow(): StateFlow<String?> = groupState.asStateFlow()

  fun getDescription(): String? {
    return state.description
  }

  fun setDescription(description: String?) {
    descriptionState.value = description
    state.description = description
  }

  fun descriptionFlow(): StateFlow<String?> = descriptionState.asStateFlow()
}