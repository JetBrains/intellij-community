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
  val useAsInlineVariant: Boolean
}

internal data class XLineBreakpointInstallationInfo(
  val types: List<XLineBreakpointTypeProxy>,
  val position: XSourcePosition,
  val isTemporary: Boolean,
  val isConditional: Boolean,
  val condition: String?,
  private val canRemove: Boolean,
) {
  fun canRemoveBreakpoint() = canRemove && !isTemporary
}

private fun XLineBreakpointInstallationInfo.toRequest(hasOneBreakpoint: Boolean) = XLineBreakpointInstallationRequest(
  types.map { XBreakpointTypeId(it.id) },
  position.toRpc(),
  isTemporary,
  isConditional,
  condition,
  willRemoveBreakpointIfSingleVariant = canRemoveBreakpoint() && hasOneBreakpoint,
)

internal class VariantChoiceData(
  val variants: List<FrontendXLineBreakpointVariant>,
  private val result: CompletableFuture<XLineBreakpointProxy?>,
  private val selectionCallback: (Int) -> Unit,
) {
  fun select(variant: FrontendXLineBreakpointVariant) {
    val index = variants.indexOf(variant)
    selectionCallback(index)
  }

  fun cancel() {
    result.cancel(false)
  }

  fun breakpointRemoved() {
    result.complete(null)
  }
}

internal fun computeBreakpointProxy(
  project: Project,
  info: XLineBreakpointInstallationInfo,
  onVariantsChoice: (VariantChoiceData) -> Unit,
): CompletableFuture<XLineBreakpointProxy?> {
  // TODO: Replace with `coroutineScope.future` after IJPL-184112 is fixed
  val result = CompletableFuture<XLineBreakpointProxy?>()
  project.service<FrontendXLineBreakpointVariantService>().cs.launch {
    try {
      val singleBreakpoint = XDebuggerUtilImpl.findBreakpointsAtLine(project, info).singleOrNull()
      val response = XBreakpointTypeApi.getInstance().toggleLineBreakpoint(project.projectId(), info.toRequest(singleBreakpoint != null))
                     ?: throw kotlin.coroutines.cancellation.CancellationException()
      when (response) {
        is XRemoveBreakpointResponse -> {
          XDebuggerUtilImpl.removeBreakpointIfPossible(project, info, singleBreakpoint)
          result.complete(null)
        }
        is XLineBreakpointInstalledResponse -> {
          result.complete(createBreakpoint(project, response.breakpoint))
        }
        is XLineBreakpointMultipleVariantResponse -> {
          result.handle { _, _ ->
            response.selectionCallback.close()
          }
          val variants = response.variants.map(::FrontendXLineBreakpointVariantImpl)
          val choiceData = VariantChoiceData(variants, result) { i ->
            responseWithVariantChoice(project, result, response.selectionCallback, i)
          }
          onVariantsChoice(choiceData)
        }
      }
    }
    catch (e: Throwable) {
      result.completeExceptionally(e)
    }
  }
  return result
}

private fun responseWithVariantChoice(
  project: Project,
  result: CompletableFuture<XLineBreakpointProxy?>,
  selectionCallback: SendChannel<VariantSelectedResponse>,
  selectedIndex: Int,
) {
  project.service<FrontendXLineBreakpointVariantService>().cs.launch {
    result.compute {
      val breakpointCallback = Channel<XBreakpointDto?>()
      selectionCallback.use {
        it.send(VariantSelectedResponse(selectedIndex, breakpointCallback))
      }
      val breakpointDto = breakpointCallback.receiveCatching().getOrNull()
      createBreakpoint(project, breakpointDto)
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

private class FrontendXLineBreakpointVariantImpl(private val dto: XLineBreakpointVariantDto) : FrontendXLineBreakpointVariant {
  override val text: String get() = dto.text
  override val icon: Icon? get() = dto.icon?.icon()
  override val highlightRange: TextRange? get() = dto.highlightRange?.toTextRange()
  override val priority: Int get() = dto.priority
  override val useAsInlineVariant: Boolean get() = dto.useAsInline
}

@Service(Service.Level.PROJECT)
private class FrontendXLineBreakpointVariantService(val cs: CoroutineScope)

private inline fun <T> CompletableFuture<T>.compute(block: () -> T) {
  try {
    val result = block()
    complete(result)
  }
  catch (e: Throwable) {
    completeExceptionally(e)
  }
}
