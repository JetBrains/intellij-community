// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointProxy
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointTypeProxy
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.rpc.*
import fleet.util.channels.use
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture
import javax.swing.Icon

internal interface FrontendXLineBreakpointVariant {
  val text: String
  val icon: Icon?
  val highlightRange: TextRange?
  val priority: Int
  fun shouldUseAsInlineVariant(): Boolean
  fun select()
}

internal data class XLineBreakpointInstallationInfo(
  val types: List<XLineBreakpointTypeProxy>,
  val position: XSourcePosition,
  val isTemporary: Boolean,
  val isConditional: Boolean,
  val condition: String?,
)

private fun XLineBreakpointInstallationInfo.toRequest() = XLineBreakpointInstallationRequest(
  types.map { XBreakpointTypeId(it.id) },
  position.toRpc(),
  isTemporary,
  isConditional,
  condition,
)

internal fun computeBreakpointProxy(
  project: Project,
  info: XLineBreakpointInstallationInfo,
  result: CompletableFuture<XLineBreakpointProxy?>,
  onVariantsChoice: (List<FrontendXLineBreakpointVariant>) -> Unit,
) {
  project.service<FrontendXLineBreakpointVariantService>().cs.launch {
    try {
      val response = XBreakpointTypeApi.getInstance().getLineBreakpointVariants(project.projectId(), info.toRequest())
                     ?: throw kotlin.coroutines.cancellation.CancellationException()
      result.handle { _, _ ->
        response.selectionCallback.close()
      }
      val variants = response.variants.mapIndexed { i, dto ->
        FrontendXLineBreakpointVariantImpl(dto) {
          responseWithVariantChoice(project, result, response.selectionCallback, i)
        }
      }
      onVariantsChoice(variants)
    }
    catch (e: Throwable) {
      result.completeExceptionally(e)
    }
  }
}

private fun responseWithVariantChoice(
  project: Project,
  res: CompletableFuture<XLineBreakpointProxy?>,
  selectionCallback: SendChannel<VariantSelectedResponse>,
  selectedIndex: Int,
) {
  project.service<FrontendXLineBreakpointVariantService>().cs.launch {
    try {
      val breakpointCallback = Channel<XBreakpointDto?>()
      selectionCallback.use {
        it.send(VariantSelectedResponse(selectedIndex, breakpointCallback))
      }
      val breakpointDto = breakpointCallback.receiveCatching().getOrNull()
      res.complete(createBreakpoint(project, breakpointDto))
    }
    catch (e: Throwable) {
      res.completeExceptionally(e)
    }
  }
}

private fun createBreakpoint(
  project: Project,
  breakpointDto: XBreakpointDto?,
): XLineBreakpointProxy? {
  if (breakpointDto == null) return null
  val breakpointManagerProxy = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)
  return breakpointManagerProxy.addBreakpoint(breakpointDto) as? XLineBreakpointProxy
}

private class FrontendXLineBreakpointVariantImpl(
  private val dto: XLineBreakpointVariantDto,
  private val callback: () -> Unit,
) : FrontendXLineBreakpointVariant {
  override val text: String = dto.text
  override val icon: Icon? = dto.icon?.icon()
  override val highlightRange: TextRange? = dto.highlightRange?.toTextRange()
  override val priority: Int = dto.priority
  override fun shouldUseAsInlineVariant(): Boolean = dto.useAsInline
  override fun select() {
    callback()
  }
}

@Service(Service.Level.PROJECT)
private class FrontendXLineBreakpointVariantService(val cs: CoroutineScope)
