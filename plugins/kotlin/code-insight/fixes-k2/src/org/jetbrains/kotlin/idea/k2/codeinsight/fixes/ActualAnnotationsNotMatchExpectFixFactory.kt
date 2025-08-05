// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ModCommandAction
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.singleConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ActualAnnotationsNotMatchExpectFixFactoryCommon
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedDeclaration

internal object ActualAnnotationsNotMatchExpectFixFactory {

    val factory = KotlinQuickFixFactory.ModCommandBased { diagnostics: KaFirDiagnostic.ActualAnnotationsNotMatchExpect -> createQuickFixes(diagnostics) }

    private fun KaSession.createQuickFixes(diagnostic: KaFirDiagnostic.ActualAnnotationsNotMatchExpect): List<ModCommandAction> {
        val expectAnnotationEntry = diagnostic.incompatibilityType.expectAnnotation.psi as? KtAnnotationEntry
            ?: return emptyList()

        val removeAnnotationFix =
            ActualAnnotationsNotMatchExpectFixFactoryCommon.createRemoveAnnotationFromExpectFix(expectAnnotationEntry)

        return listOfNotNull(removeAnnotationFix) + createCopyAndReplaceAnnotationFixes(diagnostic, expectAnnotationEntry)
    }

    context (_: KaSession)
    private fun createCopyAndReplaceAnnotationFixes(
        diagnostic: KaFirDiagnostic.ActualAnnotationsNotMatchExpect,
        expectAnnotationEntry: KtAnnotationEntry,
    ): List<ModCommandAction> {
        val expectDeclaration = diagnostic.expectSymbol.psi as? KtNamedDeclaration ?: return emptyList()
        val actualDeclaration = diagnostic.actualSymbol.psi as? KtNamedDeclaration ?: return emptyList()
        val mappedIncompatibilityType = diagnostic.incompatibilityType.mapAnnotationType {
            it.psi as? KtAnnotationEntry
        }
        return ActualAnnotationsNotMatchExpectFixFactoryCommon.createCopyAndReplaceAnnotationFixes(
            expectDeclaration,
            actualDeclaration,
            expectAnnotationEntry,
            diagnostic.actualAnnotationTargetSourceElement,
            mappedIncompatibilityType,
            annotationClassIdProvider = { expectAnnotationEntry.getAnnotationClassId() }
        )
    }

    context (_: KaSession)
    private fun KtAnnotationEntry.getAnnotationClassId(): ClassId? {
        val resolvedExpectAnnotationCall = resolveToCall()?.singleConstructorCallOrNull() ?: return null
        return resolvedExpectAnnotationCall.symbol.containingClassId
    }
}