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
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.compute
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
  result: AsyncPromise<XLineBreakpointProxy?>,
  onVariantsChoice: (List<FrontendXLineBreakpointVariant>) -> Unit,
) {
  project.service<FrontendXLineBreakpointVariantService>().cs.launch {
    try {
      val response = XBreakpointTypeApi.getInstance().getLineBreakpointVariants(project.projectId(), info.toRequest())
                     ?: throw kotlin.coroutines.cancellation.CancellationException()
      result.onProcessed {
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
      result.setError(e)
    }
  }
}

private fun responseWithVariantChoice(
  project: Project,
  res: AsyncPromise<XLineBreakpointProxy?>,
  selectionCallback: SendChannel<VariantSelectedResponse>,
  selectedIndex: Int,
) {
  project.service<FrontendXLineBreakpointVariantService>().cs.launch {
    res.compute {
      val breakpointCallback = Channel<XBreakpointDto?>()
      selectionCallback.use {
        it.send(VariantSelectedResponse(selectedIndex, breakpointCallback))
      }
      val breakpointDto = breakpointCallback.receiveCatching().getOrNull() ?: return@compute null
      createBreakpoint(project, breakpointDto)
    }
  }
}

private fun createBreakpoint(
  project: Project,
  breakpointDto: XBreakpointDto,
): XLineBreakpointProxy? {
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
