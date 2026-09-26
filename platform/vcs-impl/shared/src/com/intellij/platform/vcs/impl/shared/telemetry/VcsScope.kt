// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.telemetry

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@JvmField
@ApiStatus.Internal
val VcsScope: Scope = Scope("vcs")

val VcsTracer: IJTracer
  @ApiStatus.Internal
  get() = TelemetryManager.getInstance().getTracer(VcsScope)

@ApiStatus.Internal
inline fun <T> IJTracer.trace(span: VcsTelemetrySpan, operation: (Span) -> T): T = spanBuilder(span.getName()).use(operation)

@ApiStatus.Internal
suspend inline fun <T> IJTracer.traceSuspending(
  span: VcsTelemetrySpan,
  crossinline operation: suspend CoroutineScope.(Span) -> T,
): T =
  spanBuilder(span.getName()).useWithScope { span -> operation(span) }

@ApiStatus.Internal
fun Span.withVcsAttributes(root: VirtualFile? = null) {
  if (root != null) {
    setAttribute("rootName", root.name)
  }
}
