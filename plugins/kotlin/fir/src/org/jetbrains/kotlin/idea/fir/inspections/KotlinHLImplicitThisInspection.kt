/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.fir.inspections

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtImplicitReceiver
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.api.applicator.HLApplicator
import org.jetbrains.kotlin.idea.api.applicator.HLApplicatorInput
import org.jetbrains.kotlin.idea.api.applicator.applicator
import org.jetbrains.kotlin.idea.fir.api.AbstractHLInspection
import org.jetbrains.kotlin.idea.fir.api.applicator.*
import org.jetbrains.kotlin.idea.fir.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.render

class KotlinHLImplicitThisInspection : AbstractHLInspection<KtExpression, KotlinHLImplicitThisInspection.ImplicitReceiverInfo>(
    KtExpression::class
) {

    data class ImplicitReceiverInfo(
        val receiverLabel: Name?,
        val isUnambiguousLabel: Boolean
    ) : HLApplicatorInput

    override val applicabilityRange: HLApplicabilityRange<KtExpression> = ApplicabilityRanges.SELF

    override val inputProvider: HLApplicatorInputProvider<KtExpression, ImplicitReceiverInfo> = inputProvider { expression ->
        val reference = if (expression is KtCallableReferenceExpression) expression.callableReference else expression
        val declarationSymbol = reference.mainReference?.resolveToSymbol() ?: return@inputProvider null

        // Get associated class symbol on declaration-site
        val declarationAssociatedClass = getAssociatedClass(declarationSymbol) ?: return@inputProvider null

        // Getting the implicit receiver
        val allImplicitReceivers = reference.containingKtFile.getScopeContextForPosition(reference).implicitReceivers
        getImplicitReceiverInfoOfClass(allImplicitReceivers, declarationAssociatedClass)
    }

    private fun KtAnalysisSession.getAssociatedClass(symbol: KtSymbol): KtClassOrObjectSymbol? {
        // both variables and functions are callable and only they can be referenced by "this"
        if (symbol !is KtCallableSymbol) return null
        return when (symbol) {
            is KtFunctionSymbol, is KtPropertySymbol ->
                if (symbol.isExtension) symbol.receiverType?.expandedClassSymbol else symbol.getContainingSymbol() as? KtClassOrObjectSymbol
            is KtVariableLikeSymbol -> {
                val variableType = symbol.returnType as? KtFunctionalType
                variableType?.receiverType?.expandedClassSymbol
            }
            else -> null
        }
    }

    private fun KtAnalysisSession.getImplicitReceiverInfoOfClass(
        implicitReceivers: List<KtImplicitReceiver>, associatedClass: KtClassOrObjectSymbol
    ): ImplicitReceiverInfo? {
        // We can't use "this" with label if the label is already taken
        val alreadyReservedLabels = mutableListOf<Name>()

        var isInnermostReceiver = true
        for (receiver in implicitReceivers) {
            val (receiverClass, receiverLabel) = getImplicitReceiverClassAndTag(receiver) ?: return null

            if (receiverClass == associatedClass) {
                if (receiverLabel in alreadyReservedLabels) return null
                return if (isInnermostReceiver || receiverLabel != null) ImplicitReceiverInfo(receiverLabel, isInnermostReceiver) else null
            }

            receiverLabel?.let { alreadyReservedLabels.add(it) }
            isInnermostReceiver = false
        }
        return null
    }

    private fun KtAnalysisSession.getImplicitReceiverClassAndTag(receiver: KtImplicitReceiver): Pair<KtClassOrObjectSymbol, Name?>? {
        val associatedClass = receiver.type.expandedClassSymbol ?: return null
        val associatedTag: Name? = when (val receiverSymbol = receiver.ownerSymbol) {
            is KtClassOrObjectSymbol -> receiverSymbol.name
            is KtAnonymousFunctionSymbol -> {
                val receiverPsi = receiverSymbol.psi
                val potentialLabeledPsi = receiverPsi?.parent?.parent
                if (potentialLabeledPsi is KtLabeledExpression) potentialLabeledPsi.getLabelNameAsName()
                else {
                    val potentialCallExpression = potentialLabeledPsi?.parent as? KtCallExpression
                    val potentialCallNameReference = (potentialCallExpression?.calleeExpression as? KtNameReferenceExpression)
                    potentialCallNameReference?.getReferencedNameAsName()
                }
            }
            is KtFunctionSymbol -> receiverSymbol.name
            else -> null
        }
        return Pair(associatedClass, associatedTag)
    }

    override val presentation: HLPresentation<KtExpression> = presentation { highlightType(ProblemHighlightType.INFORMATION) }

    override val applicator: HLApplicator<KtExpression, ImplicitReceiverInfo> = KotlinHLImplicitThisInspection.applicator

    companion object {

        val applicator = applicator<KtExpression, ImplicitReceiverInfo> {
            familyAndActionName(KotlinBundle.lazyMessage(("inspection.implicit.this.display.name")))
            isApplicableByPsi { expression ->
                when (expression) {
                    is KtSimpleNameExpression -> {
                        if (expression !is KtNameReferenceExpression) return@isApplicableByPsi false
                        if (expression.parent is KtThisExpression) return@isApplicableByPsi false
                        if (expression.parent is KtCallableReferenceExpression) return@isApplicableByPsi false
                        if (expression.isSelectorOfDotQualifiedExpression()) return@isApplicableByPsi false
                        val parent = expression.parent
                        if (parent is KtCallExpression && parent.isSelectorOfDotQualifiedExpression()) return@isApplicableByPsi false
                        true
                    }
                    is KtCallableReferenceExpression -> expression.receiverExpression == null
                    else -> false
                }
            }
            applyTo { expression, input ->
                expression.addImplicitThis(input)
            }
        }

        private fun KtExpression.isSelectorOfDotQualifiedExpression(): Boolean {
            val parent = parent
            return parent is KtDotQualifiedExpression && parent.selectorExpression == this
        }

        private fun KtExpression.addImplicitThis(input: ImplicitReceiverInfo) {
            val reference = if (this is KtCallableReferenceExpression) callableReference else this
            val thisExpressionText = if (input.isUnambiguousLabel) "this" else "this@${input.receiverLabel?.render()}"
            val factory = KtPsiFactory(this)
            with(reference) {
                when (parent) {
                    is KtCallExpression -> parent.replace(factory.createExpressionByPattern("$0.$1", thisExpressionText, parent))
                    is KtCallableReferenceExpression -> parent.replace(
                        factory.createExpressionByPattern(
                            "$0::$1", thisExpressionText, this
                        )
                    )
                    else -> this.replace(factory.createExpressionByPattern("$0.$1", thisExpressionText, this))
                }
            }
        }
    }
}

