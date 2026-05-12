// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.CallableImportCandidatesProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportCandidate
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportContext
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportContextWithFixedReceiverType
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportPositionType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.types.expressions.OperatorConventions

/**
 * Not registered in [org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFixProvider],
 * because it is intended to be used only when the assignment compiler plugin is enabled.
 */
@Internal
object AssignmentPluginImportQuickFixFactory : AbstractImportQuickFixFactory() {
    override fun KaSession.detectPositionContext(diagnostic: KaDiagnosticWithPsi<*>): ImportContext? {
        if (diagnostic !is KaFirDiagnostic.UnresolvedReference) return null

        val assignmentExpression = diagnostic.psi as? KtBinaryExpression ?: return null
        if (assignmentExpression.operationToken != KtTokens.EQ) return null

        val receiverExpression = assignmentExpression.left ?: return null
        val receiverType = receiverExpression.expressionType
            // Workaround for KT-86594
            ?: (receiverExpression as? KtQualifiedExpression)?.selectorExpression?.expressionType
            ?: return null

        return ImportContextWithFixedReceiverType(
            assignmentExpression.operationReference,
            ImportPositionType.DotCall,
            explicitReceiverType = receiverType,
        )
    }

    override fun provideUnresolvedNames(diagnostic: KaDiagnosticWithPsi<*>, importContext: ImportContext): Set<Name> =
        setOf(OperatorConventions.ASSIGN_METHOD)

    override fun KaSession.provideImportCandidates(
        unresolvedName: Name,
        importContext: ImportContext,
        indexProvider: KtSymbolFromIndexProvider,
    ): List<ImportCandidate> {
        val provider = CallableImportCandidatesProvider(importContext)
        return provider.collectCandidates(unresolvedName, indexProvider)
    }
}
