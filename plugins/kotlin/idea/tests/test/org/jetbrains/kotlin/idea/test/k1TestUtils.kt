// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.diagnostic.ControlFlowException
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.psi.KtFile

fun KtFile.dumpTextWithErrors(ignoreErrors: Set<DiagnosticFactory<*>> = emptySet()): String {
    val text = text
    if (InTextDirectivesUtils.isDirectiveDefined(text, DirectiveBasedActionUtils.DISABLE_ERRORS_DIRECTIVE)) return text
    val diagnostics = run {
        var lastException: Exception? = null
        for (attempt in 0 until 2) {
            try {
                analyzeWithContent().diagnostics.let {
                    return@run it
                }
            } catch (e: Exception) {
                if (e is ControlFlowException) {
                    lastException = e.cause as? Exception ?: e
                    continue
                }
                lastException = e
            }
        }
        throw lastException ?: IllegalStateException()
    }
    val errors = diagnostics.filter { diagnostic ->
        diagnostic.severity == Severity.ERROR && diagnostic.factory !in ignoreErrors
    }
    if (errors.isEmpty()) return text
    val header = errors.joinToString("\n", postfix = "\n") { "// ERROR: " + DefaultErrorMessages.render(it).replace('\n', ' ') }
    return header + text
}