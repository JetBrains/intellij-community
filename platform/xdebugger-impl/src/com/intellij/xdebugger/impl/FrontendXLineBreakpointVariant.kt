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
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.compute
import java.util.concurrent.CompletableFuture
import javax.swing.Icon

internal interface FrontendXLineBreakpointVariant {
  val text: String
  val icon: Icon?
  val highlightRange: TextRange?
  val priority: Int
  fun shouldUseAsInlineVariant(): Boolean
  fun select(res: AsyncPromise<XLineBreakpointProxy?>)
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

internal fun getFrontendLineBreakpointVariants(
  project: Project,
  info: XLineBreakpointInstallationInfo,
  completedOrCancelledCallback: Promise<*>,
): CompletableFuture<List<FrontendXLineBreakpointVariant>> {
  val coroutineScope = project.service<FrontendXLineBreakpointVariantService>().cs
  return coroutineScope.async {
    val response = XBreakpointTypeApi.getInstance().getLineBreakpointVariants(project.projectId(), info.toRequest())
                   ?: throw kotlin.coroutines.cancellation.CancellationException()

    completedOrCancelledCallback.onProcessed {
      response.selectionCallback.close()
    }

    val variantDtos = response.variants
    variantDtos.mapIndexed { i, dto ->
      FrontendXLineBreakpointVariantImpl(dto) { res ->
        coroutineScope.launch {
          res.compute {
            val breakpointCallback = Channel<XBreakpointDto?>()
            response.selectionCallback.use {
              it.send(VariantSelectedResponse(i, breakpointCallback))
            }
            val breakpointDto = breakpointCallback.receive()
            val breakpointManagerProxy = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)
            breakpointDto?.let { breakpointManagerProxy.addBreakpoint(breakpointDto) as? XLineBreakpointProxy }
          }
        }
      }
    }
  }.asCompletableFuture()
}

private class FrontendXLineBreakpointVariantImpl(
  private val dto: XLineBreakpointVariantDto,
  private val callback: (AsyncPromise<XLineBreakpointProxy?>) -> Unit,
) : FrontendXLineBreakpointVariant {
  override val text: String = dto.text
  override val icon: Icon? = dto.icon?.icon()
  override val highlightRange: TextRange? = dto.highlightRange?.toTextRange()
  override val priority: Int = dto.priority
  override fun shouldUseAsInlineVariant(): Boolean = dto.useAsInline
  override fun select(res: AsyncPromise<XLineBreakpointProxy?>) {
    callback(res)
  }
}

@Service(Service.Level.PROJECT)
private class FrontendXLineBreakpointVariantService(val cs: CoroutineScope)
