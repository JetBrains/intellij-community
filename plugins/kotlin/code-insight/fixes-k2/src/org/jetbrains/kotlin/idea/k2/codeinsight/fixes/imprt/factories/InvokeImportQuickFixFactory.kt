// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.util.OperatorNameConventions

internal object InvokeImportQuickFixFactory : AbstractImportQuickFixFactory() {
    override fun KaSession.detectPositionContext(diagnostic: KaDiagnosticWithPsi<*>): ImportContext? {
        return when (diagnostic) {
            is KaFirDiagnostic.FunctionExpected -> {
                val expression = diagnostic.psi as? KtExpression ?: return null
                val invokeReceiverType = diagnostic.type

                ImportContextWithFixedReceiverType(
                    expression,
                    ImportPositionType.OperatorCall,
                    explicitReceiverType = invokeReceiverType,
                )
            }

            is KaFirDiagnostic.UnresolvedReference,
            is KaFirDiagnostic.NoneApplicable -> {
                val psiElement = diagnostic.psi as? KtExpression ?: return null

                val invokeReceiver = when {
                    // Cases like `(... /* anything complex here */)()` (see KT-61638)
                    psiElement is KtCallExpression -> psiElement.calleeExpression

                    // Cases like `simpleName()` (see KT-76531)
                    psiElement.isCalleeExpression -> psiElement

                    else -> null
                } ?: return null

                val invokeReceiverType = run {
                    // we have no way to know the type of the receiver from the diagnostic,
                    // so we have to use in-the-air analysis of the receiver in isolation

                    val factory = KtPsiFactory.contextual(psiElement)
                    val invokeReceiverFragment = factory.createExpressionCodeFragment(invokeReceiver.text, context = psiElement)

                    invokeReceiverFragment.getContentElement()?.expressionType
                } ?: return null

                ImportContextWithFixedReceiverType(
                    psiElement,
                    ImportPositionType.OperatorCall,
                    explicitReceiverType = invokeReceiverType,
                )
            }

            is KaFirDiagnostic.UnresolvedReferenceWrongReceiver -> {
                val psiElement = diagnostic.psi as? KtExpression ?: return null

                if (!psiElement.isCalleeExpression) return null

                // for this diagnostic, AA can provide the receiver type just fine
                val invokeReceiverType = psiElement.expressionType ?: return null

                ImportContextWithFixedReceiverType(
                    psiElement,
                    ImportPositionType.OperatorCall,
                    explicitReceiverType = invokeReceiverType,
                )
            }

            else -> null
        }
    }

    override fun provideUnresolvedNames(diagnostic: KaDiagnosticWithPsi<*>, importContext: ImportContext): Set<Name> =
        setOf(OperatorNameConventions.INVOKE)

    override fun KaSession.provideImportCandidates(
        unresolvedName: Name,
        importContext: ImportContext,
        indexProvider: KtSymbolFromIndexProvider
    ): List<ImportCandidate> {
        val provider = CallableImportCandidatesProvider(importContext)
        return provider.collectCandidates(unresolvedName, indexProvider)
    }
}

private val KtExpression.isCalleeExpression: Boolean
    get() {
        val parent = parent
        return parent is KtCallExpression && parent.calleeExpression === this
    }