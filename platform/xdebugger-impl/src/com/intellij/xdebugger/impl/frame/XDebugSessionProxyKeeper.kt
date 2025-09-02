// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
internal class XDebugSessionProxyKeeper {
  private val proxyMap = mutableMapOf<XDebugSession, XDebugSessionProxy>()

  fun getOrCreateProxy(session: XDebugSession): XDebugSessionProxy {
    return proxyMap.getOrPut(session) { XDebugSessionProxy.Monolith(session) }
  }

  fun removeProxy(session: XDebugSession) {
    proxyMap.remove(session)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): XDebugSessionProxyKeeper = project.service()
  }
}

@ApiStatus.Internal
fun XDebugSession.asProxy(): XDebugSessionProxy = XDebugSessionProxyKeeper.getInstance(project).getOrCreateProxy(this)

