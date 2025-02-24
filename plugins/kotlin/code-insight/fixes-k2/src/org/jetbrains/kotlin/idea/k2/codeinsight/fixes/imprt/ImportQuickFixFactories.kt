// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory

/**
 * Note: generic unresolved references registration is handled
 * by [org.jetbrains.kotlin.idea.k2.codeinsight.fixes.KotlinFirUnresolvedReferenceQuickFixProvider]
 */
object ImportQuickFixFactories {
    val invisibleReferenceFactory: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.InvisibleReference> =
        ImportQuickFixProvider.upcast()

    val unresolvedReferenceFactory: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.UnresolvedReference> =
        ImportQuickFixProvider.upcast()

    val unresolvedReferenceWrongReceiverFactory: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.UnresolvedReferenceWrongReceiver> =
        ImportQuickFixProvider.upcast()

    val delegateSpecialFunctionMissingFactory: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.DelegateSpecialFunctionMissing> =
        ImportQuickFixProvider.upcast()

    val delegateSpecialFunctionNoneApplicableFactory: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.DelegateSpecialFunctionNoneApplicable> =
        ImportQuickFixProvider.upcast()

    val tooManyArgumentsFactory: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.TooManyArguments> =
        ImportQuickFixProvider.upcast()

    val noValueForParameterFactory: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.NoValueForParameter> =
        ImportQuickFixProvider.upcast()

    val argumentTypeMismatchFactory: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.ArgumentTypeMismatch> =
        ImportQuickFixProvider.upcast()

    val namedParameterNotFoundFactory: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.NamedParameterNotFound> =
        ImportQuickFixProvider.upcast()

    val noneApplicableFactory: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.NoneApplicable> =
        ImportQuickFixProvider.upcast()

    val wrongNumberOfTypeArgumentsFactory: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.WrongNumberOfTypeArguments> =
        ImportQuickFixProvider.upcast()

    val newInferenceNoInformationForParameterFactory: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.NewInferenceNoInformationForParameter> =
        ImportQuickFixProvider.upcast()
}

/**
 * A utility function to "cast" [KotlinQuickFixFactory.IntentionBased] with [BASE] type
 * to more specific [DERIVED] type.
 * 
 * This should be a safe cast, since the generic in [KotlinQuickFixFactory.IntentionBased] class
 * is covariant-like (`in`), even though it's not explicitly declared as such.
 * 
 * If this changes at some point, this function will no longer be needed.
 */
@Suppress("UNCHECKED_CAST")
private fun <BASE : KaDiagnosticWithPsi<*>, DERIVED : BASE> KotlinQuickFixFactory.IntentionBased<BASE>.upcast(): KotlinQuickFixFactory.IntentionBased<DERIVED> =
    this as KotlinQuickFixFactory.IntentionBased<DERIVED> 