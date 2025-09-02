// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ColoredTextContainer
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XStackFrameUiPresentationContainer
import com.intellij.xdebugger.impl.util.identityConcurrentHashMap
import com.intellij.xdebugger.impl.util.identityWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
class XFramesAsyncPresentationManager(private val cs: CoroutineScope) {
  fun createFor(framesList: XDebuggerFramesList): XFramesAsyncPresentationHandler =
    XFramesAsyncPresentationHandler(framesList, cs.childScope("XFramesAsyncPresentationHandler for $framesList"))

  companion object {
    @JvmStatic
    fun getInstance(project: Project): XFramesAsyncPresentationManager = project.service()
  }
}

class XFramesAsyncPresentationHandler(private val framesList: XDebuggerFramesList, private val cs: CoroutineScope) {

  private val cache = identityConcurrentHashMap<XStackFrame, XStackFrameUiPresentationContainer>()
  private val repaintRequests = MutableSharedFlow<Unit>()

  fun scheduleForFrames(stackFrames: List<XStackFrame>) {
    for (stackFrame in stackFrames) {
      cs.launch(Dispatchers.Default) {
        stackFrame.customizePresentation().collectLatest { newPresentation ->
          cache += stackFrame.identityWrapper() to newPresentation
          repaintRequests.emit(Unit)
          withContext(Dispatchers.EDT) {
            // TODO cooldown period to reduce invocations count
            framesList.repaint()
          }
        }
      }
    }
  }

  fun clear(): Unit = cache.clear()

  fun customizePresentation(stackFrame: XStackFrame, container: ColoredTextContainer) {
    cache[stackFrame.identityWrapper()]?.customizePresentation(container)
  }

  fun sessionStopped() {
    clear()
    cs.cancel()
  }
}
