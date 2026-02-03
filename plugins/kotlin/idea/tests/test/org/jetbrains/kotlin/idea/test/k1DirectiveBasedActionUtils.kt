// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test

import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics

val k1DiagnosticsProvider: (KtFile) -> List<Diagnostic> =
    mapFromK1Provider { it.analyzeWithContent().diagnostics }

fun mapFromK1Provider(diagnosticsProvider: (KtFile) -> Diagnostics): (KtFile) -> List<Diagnostic> = { file ->
    diagnosticsProvider(file).map { Diagnostic(DefaultErrorMessages.render(it), it.severity) }
}