// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.components.diagnostics
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty
import kotlin.reflect.KClass

private val SAVED_DIAGNOSTIC_CLASSES: Key<Set<KClass<out KaDiagnosticWithPsi<*>>>> = Key("KOTLIN_SAVED_DIAGNOSTIC_CLASSES")

private var PsiElement.relevantDiagnosticClasses: Set<KClass<out KaDiagnosticWithPsi<*>>>
        by NotNullableUserDataProperty(SAVED_DIAGNOSTIC_CLASSES, defaultValue = emptySet())

/**
 * Tries to restore a set of relevant [KaDiagnosticWithPsi] associated with unresolved reference at [PsiElement].
 * Supposed to be used by the Auto-Import subsystem in K2 Mode.
 *
 * This function currently relies on two assumptions:
 * 1. All the relevant diagnostics can be correctly obtained by calling [KaSession.diagnostics] on [this] element.
 * There are potentially diagnostics that would be lost and only available by calling [KaSession.collectDiagnostics]
 * on the whole file; however, there are no such examples yet.
 * 2. If there was a diagnostic class stored in the [relevantDiagnosticClasses],
 * it means that ALL diagnostics of that type would be relevant for the caller of this method,
 * and all of them are returned.
 *
 * N.B. If Analysis API introduces a safer or more efficient way to transfer diagnostics
 * between Analysis Sessions (for example, by pointers, see KT-74573), this mechanism should also use it.
 */
context(_: KaSession)
@OptIn(KaExperimentalApi::class)
@ApiStatus.Internal
fun KtElement.restoreKaDiagnosticsForUnresolvedReference(): Set<KaDiagnosticWithPsi<*>> {
    val element = this
    
    val expectedDiagnosticClasses = relevantDiagnosticClasses
    if (expectedDiagnosticClasses.isEmpty()) return emptySet()

    val freshDiagnostics = element.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)

    return freshDiagnostics
        .filter { it.diagnosticClass in expectedDiagnosticClasses }
        .toSet()
}

/**
 * Saves [diagnostic] so that it can be restored for [this] element by [restoreKaDiagnosticsForUnresolvedReference].
 */
context(_: KaSession)
@ApiStatus.Internal
fun PsiElement.saveKaDiagnosticForUnresolvedReference(diagnostic: KaDiagnosticWithPsi<*>) {
    relevantDiagnosticClasses += diagnostic.diagnosticClass
}

/**
 * Clears all data for [this] element related to [restoreKaDiagnosticsForUnresolvedReference].
 */
@ApiStatus.Internal
fun PsiElement.clearSavedKaDiagnosticsForUnresolvedReference() {
    relevantDiagnosticClasses = emptySet()
}

/* 
Utils below are required to overcome poorly selected PSI elements 
on UnresolvedReference diagnostics for complex assignments (like `+=`).

See KT-75331 for more info. 
*/

@get:ApiStatus.Internal
val PsiElement.operationReferenceForBinaryExpressionOrThis: PsiElement
    get() = (this as? KtBinaryExpression)?.operationReference ?: this

@get:ApiStatus.Internal
val PsiElement.binaryExpressionForOperationReference: KtBinaryExpression?
    get() = (this as? KtOperationReferenceExpression)?.parent as? KtBinaryExpression
