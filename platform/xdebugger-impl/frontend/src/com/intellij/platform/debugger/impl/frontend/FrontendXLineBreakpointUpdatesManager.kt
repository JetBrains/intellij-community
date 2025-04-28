// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
internal class FrontendXLineBreakpointUpdatesManager(private val cs: CoroutineScope) {
  private val mergingUpdateQueue = MergingUpdateQueue.mergingUpdateQueue(
    name = "Frontend XLine breakpoints",
    mergingTimeSpan = 300,
    coroutineScope = cs,
  )

  fun queueBreakpointUpdate(breakpoint: XBreakpointProxy, callback: () -> Unit) {
    mergingUpdateQueue.queue(object : Update(breakpoint) {
      override fun run() {
        callback()
      }
    })
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): FrontendXLineBreakpointUpdatesManager = project.service<FrontendXLineBreakpointUpdatesManager>()
  }
}