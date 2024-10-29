// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.KernelService
import com.intellij.platform.kernel.util.kernelCoroutineContext
import com.intellij.platform.project.asEntity
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.impl.XDebuggerActiveSessionEntity
import fleet.kernel.rete.Rete
import fleet.kernel.rete.each
import fleet.kernel.rete.filter
import fleet.kernel.rete.tokensFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus

@Service(Service.Level.PROJECT)
internal class FrontendXDebuggerManager(private val project: Project, private val cs: CoroutineScope) {
  private var sessionCoroutineScope: CoroutineScope? = null

  @OptIn(ExperimentalCoroutinesApi::class)
  val currentSession: StateFlow<FrontendXDebuggerSession?> =
    XDebuggerActiveSessionEntity.each()
      .filter { it.projectEntity == project.asEntity() }
      .tokensFlow()
      .map { token ->
        sessionCoroutineScope?.cancel()
        if (token.added) {
          // TODO[IJPL-160146]: Service coroutine scope should contain Rete by default
          val newSessionCoroutineScope = cs.childScope("FrontendXDebuggerSession") + KernelService.instance.kernelCoroutineScope.getCompleted().coroutineContext.kernelCoroutineContext()
          sessionCoroutineScope = newSessionCoroutineScope
          FrontendXDebuggerSession(project, newSessionCoroutineScope, token.value)
        }
        else {
          sessionCoroutineScope = null
          null
        }
      }
      // TODO[IJPL-160146]: Service coroutine scope should contain Rete by default
      .stateIn(cs + KernelService.instance.kernelCoroutineScope.getCompleted().coroutineContext.kernelCoroutineContext(), SharingStarted.Eagerly, null)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): FrontendXDebuggerManager = project.service()
  }
}