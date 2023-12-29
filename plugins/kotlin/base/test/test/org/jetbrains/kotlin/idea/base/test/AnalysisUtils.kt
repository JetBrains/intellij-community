// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ResolutionUtils")
package org.jetbrains.kotlin.idea.base.test

import com.intellij.openapi.application.runReadAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtFile

/**
 * Performs all-phase analysis of the given [files].
 * Does nothing if all declarations in files are already resolved.
 */
@OptIn(KtAllowAnalysisOnEdt::class)
fun ensureFilesResolved(vararg files: KtFile) {
    // Unless the session is created for the module, its invalidation won't trigger invalidation of dependents.
    // See 'didSessionExist' in 'LLFirSessionInvalidationService.invalidate()'.
    // Also see 'getCachedFileStructure()' in 'LLFirDeclarationModificationService.inBlockModification'.
    allowAnalysisOnEdt {
        runReadAction {
            for (file in files) {
                analyze(file) {
                    file.collectDiagnosticsForFile(KtDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
                }
            }
        }
    }
}