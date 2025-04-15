// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.util.OperatorNameConventions

internal object InvokeImportQuickFixFactory : AbstractImportQuickFixFactory() {
    override fun KaSession.detectPositionContext(diagnostic: KaDiagnosticWithPsi<*>): ImportContext? {
        val psiElement = diagnostic.psi as? KtExpression ?: return null

        val invokeReceiverType = when (diagnostic) {
            is KaFirDiagnostic.FunctionExpected -> {
                diagnostic.type
            }

            is KaFirDiagnostic.UnresolvedReference,
            is KaFirDiagnostic.NoneApplicable -> {
                val invokeCallReceiver = when {
                    // Cases like `(... /* anything complex here */)()` (see KT-61638)
                    psiElement is KtCallExpression -> psiElement.calleeExpression

                    // Cases like `simpleName()` (see KT-76531)
                    psiElement.isCalleeExpression -> psiElement

                    else -> null
                } ?: return null

                val invokeCall = invokeCallReceiver.getCallExpressionForCallee() ?: return null
                val qualifiedInvokeCall = invokeCall.getQualifiedExpressionForSelectorOrThis()

                val invokeCallReceiverCopy = qualifiedInvokeCall.copyQualifiedCalleeExpression() ?: return null

                // we have no way to know the type of the receiver from the diagnostic,
                // so we have to use in-the-air analysis of the receiver in isolation
                invokeCallReceiverCopy.expressionType
            }

            is KaFirDiagnostic.UnresolvedReferenceWrongReceiver -> {
                if (!psiElement.isCalleeExpression) return null

                // for this diagnostic, AA can provide the receiver type just fine
                psiElement.expressionType
            }

            else -> null
        } ?: return null

        return ImportContextWithFixedReceiverType(
            psiElement,
            ImportPositionType.OperatorCall,
            explicitReceiverType = invokeReceiverType,
        )
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
    get() = getCallExpressionForCallee() != null

private fun KtExpression.getCallExpressionForCallee(): KtCallExpression? {
    return (parent as? KtCallExpression)?.takeIf { it.calleeExpression === this }
}

/**
 * For [KtExpression] representing a possibly qualified function call,
 * returns a copy [KtExpression] representing only the callee expression with a possible qualifier.
 *
 * See [qualifiedCalleeExpressionTextRangeInThis] for example.
 */
private fun KtExpression.copyQualifiedCalleeExpression(): KtExpression? {
    val possiblyQualifiedCall = this

    val calleeRelativeRange = possiblyQualifiedCall.qualifiedCalleeExpressionTextRangeInThis ?: return null
    val calleeText = calleeRelativeRange.substring(possiblyQualifiedCall.text)

    val factory = KtPsiFactory.contextual(possiblyQualifiedCall)
    val calleeExpressionFragment = factory.createExpressionCodeFragment(calleeText, context = possiblyQualifiedCall)

    return calleeExpressionFragment.getContentElement()
}

/**
 * For [KtExpression] representing a possibly qualified function call,
 * returns an absolute [TextRange] which represents only the callee expression with a possible qualifier.
 *
 * Some examples:
 * - `foo(...)` -> `foo`
 * - `foo.bar(...)` -> `foo.bar`
 * - `foo.baz(...).bar { ... }` -> `foo.baz(...).bar`
 * - `(<complex expression>)(...)` -> `(<complex expression>)`
 */
private val KtExpression.qualifiedCalleeExpressionTextRangeInThis: TextRange?
    get() {
        val possiblyQualifiedCall = this

        val callExpression = possiblyQualifiedCall.getPossiblyQualifiedCallExpression() ?: return null
        val calleeExpression = callExpression.calleeExpression ?: return null

        val calleeExpressionEnd = calleeExpression.textRangeIn(possiblyQualifiedCall).endOffset

        return TextRange.create(0, calleeExpressionEnd)
    }
