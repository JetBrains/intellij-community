// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.resolve.util

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cfg.ControlFlowInformationProviderImpl
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.NotNullablePsiCopyableUserDataProperty
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.DelegatingBindingTrace
import org.jetbrains.kotlin.resolve.lazy.ResolveSession

@K1Deprecation
fun analyzeControlFlow(resolveSession: ResolveSession, resolveElement: KtElement, trace: BindingTrace) {
    val controlFlowTrace = DelegatingBindingTrace(
        trace.bindingContext, "Element control flow resolve", resolveElement, allowSliceRewrite = true
    )
    ControlFlowInformationProviderImpl(
        resolveElement, controlFlowTrace, resolveElement.languageVersionSettings, resolveSession.platformDiagnosticSuppressor
    ).checkDeclaration()
    controlFlowTrace.addOwnDataTo(trace, filter = null, commitDiagnostics = resolveElement.containingKtFile.reportControlFlowDiagnosticsInK1)
}

/**
 * CFG analysis can emit different diagnostics (`VAL_REASSIGNMENT`, for example), and they are dropped by default in [analyzeControlFlow].
 * Even if they are created and reported, they are not carried to the main [BindingTrace] from the [ControlFlowInformationProviderImpl]
 * when [analyzeControlFlow] is executed.
 *
 * There are few reasons for that:
 *
 * - When [analyzeWithAllCompilerChecks][org.jetbrains.kotlin.idea.resolve.ResolutionFacade.analyzeWithAllCompilerChecks] is called,
 * it may also collect all those diagnostics on the Kotlin Frontend side.
 * That might lead to repeating diagnostics, which is annoying and can break a lot of call-sites.
 * - Enabling this by default might prevent some tricky code in the Debugger Evaluator from being compiled.
 * Evaluator is supposed to allow some non-compilable code to be compilable (e.g., re-assigning final local variables).
 * - Some call-sites of the K1 frontend API might already expect that CFG diagnostics are not reported.
 *
 * However, there is an obvious drawback: if someone **needs** such kind of diagnostics reported without calling the potentially expensive
 * [analyzeWithAllCompilerChecks][org.jetbrains.kotlin.idea.resolve.ResolutionFacade.analyzeWithAllCompilerChecks], they will not be
 * satisfied with the default behavior.
 * That includes all the calls to the regular [analyze][org.jetbrains.kotlin.idea.resolve.ResolutionFacade.analyze], even with the
 * [org.jetbrains.kotlin.resolve.lazy.BodyResolveMode.doControlFlowAnalysis] set to `true`.
 *
 * This [KtFile]-level flag allows **overriding that default behavior to report the CFG diagnostics**, effectively ignoring all the
 * considerations above.
 * Just to be clear: if you call
 * [analyzeWithAllCompilerChecks][org.jetbrains.kotlin.idea.resolve.ResolutionFacade.analyzeWithAllCompilerChecks]
 * with this flag set to `true`, **you will get duplicated CFG-related diagnostics**.
 *
 * Rationale for this flag introduction:
 * While this flag does not "fix" the issue of missing CFG diagnostics and is essentially a workaround, it was considered
 * to be a better alternative compared to changing the default behavior of
 * the [analyzeControlFlow] without a careful evaluation of possible side effects.
 *
 * It should not be used by external users, and should be avoided by internal ones.
 * Please consider talking to the Kotlin IntelliJ Plugin team if you have a need to use this flag for any reason.
 *
 * N.B. This flag is K1-frontend specific; it does not have any effect on K2.
 */
@K1Deprecation
var KtFile.reportControlFlowDiagnosticsInK1: Boolean by NotNullablePsiCopyableUserDataProperty(Key.create("REPORT_CONTROL_FLOW_DIAGNOSTICS_IN_K1"), false)
    @ApiStatus.Internal get
    @ApiStatus.Internal set