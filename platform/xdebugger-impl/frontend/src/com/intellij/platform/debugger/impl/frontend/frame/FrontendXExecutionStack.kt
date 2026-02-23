// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.storage.findStackFrame
import com.intellij.platform.debugger.impl.frontend.storage.getOrCreateStackFrame
import com.intellij.platform.debugger.impl.rpc.ComputeFramesConfig
import com.intellij.platform.debugger.impl.rpc.XExecutionStackApi
import com.intellij.platform.debugger.impl.rpc.XExecutionStackDto
import com.intellij.platform.debugger.impl.rpc.XExecutionStackId
import com.intellij.platform.debugger.impl.rpc.XStackFramesEvent
import com.intellij.platform.debugger.impl.ui.XDebuggerEntityConverter
import com.intellij.xdebugger.frame.XDescriptor
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.settings.XDebuggerSettingsManager
import fleet.util.logging.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture

internal class FrontendXExecutionStack(
  private val stackDto: XExecutionStackDto,
  private val project: Project,
  private val suspendContextLifetimeScope: CoroutineScope,
) : XExecutionStack(stackDto.displayName, stackDto.icon?.icon()) {
  init {
    suspendContextLifetimeScope.launch {
      stackDto.iconFlow.toFlow().collect { iconId -> icon = iconId?.icon() }
    }
  }

  val id: XExecutionStackId = stackDto.executionStackId

  private val topValue: CompletableFuture<XStackFrame?> = stackDto.topFrame.asCompletableFuture().thenApply<XStackFrame> { frameDto ->
    if (frameDto == null) return@thenApply null
    suspendContextLifetimeScope.getOrCreateStackFrame(frameDto, project)
  }.exceptionally {
    null
  }

  override fun getTopFrame(): XStackFrame? {
    return topValue.getNow(null)
  }

  override fun getTopFrameAsync(): CompletableFuture<XStackFrame?> {
    return topValue
  }

  override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
    suspendContextLifetimeScope.launch {
      XExecutionStackApi.getInstance().computeStackFrames(id, firstFrameIndex, createComputeFramesConfig()).collect { event ->
        when (event) {
          is XStackFramesEvent.ErrorOccurred -> {
            container.errorOccurred(event.errorMessage)
          }
          is XStackFramesEvent.XNewStackFrames -> {
            // TODO[IJPL-177087]: here we are binding FrontendXExecutionStack to the suspend context scope,
            //  which is the safest-narrowest scope in our possession.
            //  However, maybe it's possible to set up, for example, a scope that ends when another stack is selected from a combobox.
            //  But it requires further investigation.
            val feFrames = event.frames.map { suspendContextLifetimeScope.getOrCreateStackFrame(it, project) }
            container.addStackFrames(feFrames, event.last)
          }
          is XStackFramesEvent.NewPresentation -> {
            val frame = suspendContextLifetimeScope.findStackFrame(event.stackFrameId)
            if (frame == null) {
              logger.warn("Frame with id ${event.stackFrameId} not found. Probably presentation event was received earlier than stack frame")
              return@collect
            }
            frame.newUiPresentation(event.presentation)
          }
        }
      }
    }
  }

  private fun createComputeFramesConfig(): ComputeFramesConfig = ComputeFramesConfig(
    XDebuggerSettingsManager.getInstance().dataViewSettings.isShowLibraryStackFrames,
  )

  override fun equals(other: Any?): Boolean {
    return other is FrontendXExecutionStack && other.id == id
  }

  override fun getXExecutionStackDescriptorAsync(): CompletableFuture<XDescriptor?>? {
    return stackDto.descriptor?.asCompletableFuture()
  }

  override fun getExecutionLineIconRenderer(): GutterIconRenderer? {
    // TODO Supported only in monolith
    return XDebuggerEntityConverter.getExecutionStack(id)?.executionLineIconRenderer
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }

  companion object {
    private val logger = logger<FrontendXExecutionStack>()
  }
}
