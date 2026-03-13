// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.ide.rpc.util.textRange
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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
import fleet.util.channels.use
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import javax.swing.Icon

@ApiStatus.Internal
interface FrontendXLineBreakpointVariant {
  val text: String
  val icon: Icon?
  val highlightRange: TextRange?
  val priority: Int
  val useAsInlineVariant: Boolean
}

@ApiStatus.Internal
fun XLineBreakpointInstallationInfo.toRequest(hasBreakpoints: Boolean): XLineBreakpointInstallationRequest = XLineBreakpointInstallationRequest(
  types.map { XBreakpointTypeId(it.id) },
  position.toRpc(),
  isTemporary,
  isLogging,
  logExpression,
  hasBreakpoints,
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
  editor: Editor?,
  info: XLineBreakpointInstallationInfo,
  onVariantsChoice: (VariantChoiceData) -> Unit,
): CompletableFuture<XLineBreakpointProxy?> {
  // TODO: Replace with `coroutineScope.future` after IJPL-184112 is fixed
  val result = CompletableFuture<XLineBreakpointProxy?>()
  project.service<FrontendXLineBreakpointVariantService>().cs.launch {
    try {
      val breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)
      breakpointManager.withLightBreakpointIfPossible(editor, info) {
        val breakpointExists = XBreakpointUIUtil.findBreakpointsAtLine(project, info).isNotEmpty()
        val response = XBreakpointTypeApi.getInstance()
                         .toggleLineBreakpoint(project.projectId(), info.toRequest(breakpointExists))
                       ?: throw kotlin.coroutines.cancellation.CancellationException()
        when (response) {
          is XRemoveBreakpointResponse -> {
            val breakpoint = XBreakpointUIUtil.findBreakpointsAtLine(project, info).firstOrNull()
            if (breakpoint != null) {
              XBreakpointUIUtil.removeBreakpointIfPossible(info, breakpoint).await()
            }
            result.complete(null)
          }
          is XLineBreakpointInstalledResponse -> {
            result.complete(createBreakpoint(project, response.breakpointId))
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
          XNoBreakpointPossibleResponse -> {
            result.complete(null)
          }
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
      val breakpointCallback = Channel<XBreakpointId>()
      selectionCallback.use {
        it.send(VariantSelectedResponse(selectedIndex, breakpointCallback))
      }
      try {
        val breakpointId = breakpointCallback.receive()
        createBreakpoint(project, breakpointId)
      }
      catch (_: ClosedReceiveChannelException) {
        null
      }
    }
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
