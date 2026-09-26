// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.ide.rpc.util.textRange
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.debugger.impl.rpc.VariantSelectedResponse
import com.intellij.platform.debugger.impl.rpc.XBreakpointId
import com.intellij.platform.debugger.impl.rpc.XBreakpointTypeApi
import com.intellij.platform.debugger.impl.rpc.XBreakpointTypeId
import com.intellij.platform.debugger.impl.rpc.XLineBreakpointInstallationRequest
import com.intellij.platform.debugger.impl.rpc.XLineBreakpointInstalledResponse
import com.intellij.platform.debugger.impl.rpc.XLineBreakpointMultipleVariantResponse
import com.intellij.platform.debugger.impl.rpc.XLineBreakpointVariantDto
import com.intellij.platform.debugger.impl.rpc.XNoBreakpointPossibleResponse
import com.intellij.platform.debugger.impl.rpc.XRemoveBreakpointResponse
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointInstallationInfo
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUIUtil
import com.intellij.xdebugger.impl.rpc.toRpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.SendChannel
import javax.swing.Icon
import com.intellij.platform.debugger.impl.rpc.toRpc as expressionToRpc

internal interface FrontendXLineBreakpointVariant {
  val text: String
  val icon: Icon?
  val highlightRange: TextRange?
  val priority: Int
  val useAsInlineVariant: Boolean
}

private suspend fun XLineBreakpointInstallationInfo.toRequest(hasBreakpoints: Boolean) = XLineBreakpointInstallationRequest(
  types.map { XBreakpointTypeId(it.id) },
  position.toRpc(),
  placement,
  isTemporary,
  isLogging,
  logExpression?.expressionToRpc(),
  hasBreakpoints,
)

internal suspend fun computeBreakpointProxy(
  project: Project,
  editor: Editor?,
  info: XLineBreakpointInstallationInfo,
  onVariantsChoice: suspend (List<FrontendXLineBreakpointVariant>) -> FrontendXLineBreakpointVariant?,
): XLineBreakpointProxy? {
  val breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)
  return breakpointManager.withLightBreakpointIfPossible(editor, info) {
    val breakpointExists = XBreakpointUIUtil.findBreakpointsAtLine(project, info).isNotEmpty()
    when (val response = XBreakpointTypeApi.getInstance()
      .toggleLineBreakpoint(project.projectId(), info.toRequest(breakpointExists))) {
      is XRemoveBreakpointResponse -> {
        val breakpoints = XBreakpointUIUtil.findBreakpointsAtLine(project, info)
        if (breakpoints.isNotEmpty()) {
          XBreakpointUIUtil.removeBreakpointIfPossible(info, *breakpoints.toTypedArray())
        }
        null
      }
      is XLineBreakpointInstalledResponse -> createBreakpoint(project, response.breakpointId)
      is XLineBreakpointMultipleVariantResponse -> try {
        val variants = response.variants.map(::FrontendXLineBreakpointVariantImpl)
        val selected = onVariantsChoice(variants) ?: return@withLightBreakpointIfPossible null
        val selectedIndex = variants.indexOf(selected)
        responseWithVariantChoice(project, response.selectionCallback, selectedIndex)
      }
      finally {
        response.selectionCallback.close()
      }
      XNoBreakpointPossibleResponse -> null
      null -> throw CancellationException()
    }
  }
}

private suspend fun responseWithVariantChoice(
  project: Project,
  selectionCallback: SendChannel<VariantSelectedResponse>,
  selectedIndex: Int,
): XLineBreakpointProxy? {
  val breakpointCallback = Channel<XBreakpointId>()
  selectionCallback.send(VariantSelectedResponse(selectedIndex, breakpointCallback))
  return try {
    val breakpointId = breakpointCallback.receive()
    createBreakpoint(project, breakpointId)
  }
  catch (_: ClosedReceiveChannelException) {
    null
  }
  finally {
    breakpointCallback.cancel()
  }
}

private suspend fun createBreakpoint(
  project: Project,
  breakpointId: XBreakpointId,
): XLineBreakpointProxy? {
  val breakpointManagerProxy = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)
  return breakpointManagerProxy.awaitBreakpointCreation(breakpointId) as? XLineBreakpointProxy
}

private class FrontendXLineBreakpointVariantImpl(private val dto: XLineBreakpointVariantDto) : FrontendXLineBreakpointVariant {
  override val text: String get() = dto.text
  override val icon: Icon? get() = dto.icon?.icon()
  override val highlightRange: TextRange? get() = dto.highlightRange?.textRange()
  override val priority: Int get() = dto.priority
  override val useAsInlineVariant: Boolean get() = dto.useAsInline
}
