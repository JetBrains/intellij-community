// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.util

import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.vcs.impl.shared.telemetry.VcsTelemetrySpan
import io.opentelemetry.api.trace.Span

internal inline fun <T> IJTracer.trace(span: VcsTelemetrySpan, operation: (Span) -> T): T = spanBuilder(span.getName()).use(operation)