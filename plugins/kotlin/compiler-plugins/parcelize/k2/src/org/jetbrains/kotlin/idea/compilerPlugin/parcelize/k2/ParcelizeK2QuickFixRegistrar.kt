// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.k2

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KtDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtCompilerPluginDiagnostic0
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtCompilerPluginDiagnostic1
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.diagnostics.AbstractKtDiagnosticFactory
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixRegistrar
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KtQuickFixesListBuilder
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactoryFromIntentionActions
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes.*
import org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
import org.jetbrains.kotlin.parcelize.fir.diagnostics.KtErrorsParcelize
import org.jetbrains.kotlin.psi.KtClassOrObject

class ParcelizeK2QuickFixRegistrar : KotlinQuickFixRegistrar() {
    override val list = KtQuickFixesListBuilder.registerPsiQuickFix {
        registerQuickFixForDiagnosticFactory(
            KtErrorsParcelize.PARCELABLE_CANT_BE_INNER_CLASS,
            RemoveModifierFixBase.removeInnerModifier
        )
        registerQuickFixForDiagnosticFactory(
            KtErrorsParcelize.NO_PARCELABLE_SUPERTYPE,
            ParcelizeAddSupertypeQuickFix.FACTORY
        )
        registerQuickFixForDiagnosticFactory(
            KtErrorsParcelize.PARCELABLE_SHOULD_HAVE_PRIMARY_CONSTRUCTOR,
            ParcelizeAddPrimaryConstructorQuickFix.FACTORY
        )
        registerQuickFixForDiagnosticFactory(
            KtErrorsParcelize.PROPERTY_WONT_BE_SERIALIZED,
            ParcelizeAddIgnoreOnParcelAnnotationQuickFix.FACTORY
        )
        registerQuickFixForDiagnosticFactory(
            KtErrorsParcelize.REDUNDANT_TYPE_PARCELER,
            ParcelizeRemoveDuplicatingTypeParcelerAnnotationQuickFix.FACTORY
        )

        registerQuickFixForDiagnosticFactory(
            KtErrorsParcelize.OVERRIDING_WRITE_TO_PARCEL_IS_NOT_ALLOWED,
            K2ParcelMigrateToParcelizeQuickFix.FACTORY_FOR_WRITE
        )
        registerQuickFixForDiagnosticFactory(
            KtErrorsParcelize.OVERRIDING_WRITE_TO_PARCEL_IS_NOT_ALLOWED,
            ParcelRemoveCustomWriteToParcel.FACTORY
        )

        registerQuickFixForDiagnosticFactory(
            KtErrorsParcelize.CREATOR_DEFINITION_IS_NOT_ALLOWED,
            K2ParcelMigrateToParcelizeQuickFix.FACTORY_FOR_CREATOR
        )
        registerQuickFixForDiagnosticFactory(
            KtErrorsParcelize.CREATOR_DEFINITION_IS_NOT_ALLOWED,
            ParcelRemoveCustomCreatorProperty.FACTORY
        )

        registerApplicator(
            createApplicatorForFactory<KtCompilerPluginDiagnostic1>(KtErrorsParcelize.CLASS_SHOULD_BE_PARCELIZE) { diagnostic ->
                val parameterSymbol = diagnostic.parameter1 as? KtClassOrObjectSymbol
                val parameterPsi = parameterSymbol?.psi as? KtClassOrObject
                if (parameterPsi != null) {
                    listOf(AnnotateWithParcelizeQuickFix(parameterPsi))
                } else {
                    listOf()
                }
            }
        )
    }
}

private fun KtQuickFixesListBuilder.registerQuickFixForDiagnosticFactory(
    diagnosticFactory: KtDiagnosticFactory0,
    quickFixFactory: QuickFixesPsiBasedFactory<*>
) {
    registerApplicator(
        createApplicatorForFactory<KtCompilerPluginDiagnostic0>(diagnosticFactory) {
            quickFixFactory.createQuickFix(it.psi)
        }
    )
}

private fun KtQuickFixesListBuilder.registerQuickFixForDiagnosticFactory(
    diagnosticFactory: KtDiagnosticFactory1<*>,
    quickFixFactory: QuickFixesPsiBasedFactory<*>
) {
    registerApplicator(
        createApplicatorForFactory<KtCompilerPluginDiagnostic1>(diagnosticFactory) {
            quickFixFactory.createQuickFix(it.psi)
        }
    )
}

private inline fun <reified DIAGNOSTIC : KtDiagnosticWithPsi<*>> createApplicatorForFactory(
    factory: AbstractKtDiagnosticFactory,
    crossinline createQuickFixes:  context(KtAnalysisSession)(DIAGNOSTIC) -> List<IntentionAction>
) = diagnosticFixFactoryFromIntentionActions(DIAGNOSTIC::class) { diagnostic ->
    if (diagnostic.factoryName == factory.name) {
        createQuickFixes(analysisSession, diagnostic)
    } else {
        emptyList()
    }
}
