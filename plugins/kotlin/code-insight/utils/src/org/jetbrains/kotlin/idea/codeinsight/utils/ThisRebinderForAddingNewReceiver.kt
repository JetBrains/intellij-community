// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.isAncestor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType

/**
 * Adding a new implicit receiver around existing code may affect code resolution.
 *
 * To avoid this, we add explicit `this@<LABEL>` to calls that use implicit receivers and add additional labels to `this` without ones.
 */
@ApiStatus.Internal
object ThisRebinderForAddingNewReceiver {
    @OptIn(KaContextParameterApi::class)
    context(_: KaSession)
    fun createContext(element: KtElement): Context {
        val nonDotCalls = mutableListOf<KtExpression>()
        val unlabelledThisExpressions = mutableListOf<KtThisExpression>()
        element.forEachDescendantOfType<KtElement> { element ->
            when {
                element is KtCallExpression
                        && (element.parent as? KtQualifiedExpression)?.selectorExpression != element -> {
                    nonDotCalls += element
                }

                element is KtNameReferenceExpression
                        && (element.parent as? KtCallExpression)?.calleeExpression != element
                        && (element.parent as? KtQualifiedExpression)?.selectorExpression != element -> {
                    nonDotCalls += element
                }

                element is KtThisExpression && element.getLabelName() == null -> {
                    unlabelledThisExpressions += element
                }
            }
        }
        val callsToAddImplicitReceiver = nonDotCalls.mapNotNull { expression ->
            val info = expression.getImplicitReceiverInfo() ?: return@mapNotNull null
            if (!info.receiverProvidedBy.isAncestor(element)) {
                // If an element provided the scope is outside our binary expression. Otherwise, the resolution is not affected
                return@mapNotNull null
            }
            val label = info.receiverLabel?.asString() ?: return@mapNotNull null
            Context.CallToAddImplicitReceiver(expression.createSmartPointer(), label)
        }
        val thisExpressionsToAddLabels = unlabelledThisExpressions.mapNotNull { thisExpression ->
            val target = thisExpression.instanceReference.mainReference.resolveToSymbol() ?: return@mapNotNull null
            val info = when (target) {
                is KaReceiverParameterSymbol -> getLabelToBeReferencedByThis(target.owningCallableSymbol)
                else -> getLabelToBeReferencedByThis(target)
            }
            val label = info?.receiverLabel?.asString() ?: return@mapNotNull null
            if (!info.receiverProvidedBy.isAncestor(element)) {
                // If an element provided the scope is outside our binary expression. Otherwise, the resolution is not affected
                return@mapNotNull null
            }
            Context.ThisExpressionToAddLabel(thisExpression.createSmartPointer(), label)
        }
        return Context(
            callsToAddImplicitReceiver,
            thisExpressionsToAddLabels,
            element.project
        )
    }

    fun apply(context: Context): Map<KtExpression, KtExpression> {
        val ktPsiFactory = KtPsiFactory(context.project)
        val replacements = mutableMapOf<KtExpression, KtExpression>()

        for (call in context.callsToAddImplicitReceiver) {
            val psi = call.call.element ?: continue
            val qualified = psi.replaced(
                ktPsiFactory.createQualifiedThisAccess(call.labelToAdd, psi)
            )
            val selector = qualified.selectorExpression
                ?: error("Expected selector to be present for the qualified expression created by KtPsiFactory")
            replacements[selector] = qualified
        }

        for (thisExpression in context.thisExpressionsToAddLabels) {
            val psi = thisExpression.thisExpression.element ?: continue
            val newLabel = ktPsiFactory.createThisExpression(thisExpression.labelToAdd).labelQualifier
                ?: error("Expected label for `this` expression created by KtPsiFactory")
            psi.addAfter(newLabel, psi.instanceReference)
        }

        return replacements
    }

    private fun KtPsiFactory.createQualifiedThisAccess(thisLabelName: String, selector: KtExpression): KtQualifiedExpression {
        val thisQualifiedExpression = createExpression("this@${thisLabelName}.aaa") as KtQualifiedExpression
        val selectorExpression = thisQualifiedExpression.selectorExpression
            ?: error("Expected an KtQualifiedExpression to have a selector expression")
        selectorExpression.replace(selector)
        return thisQualifiedExpression
    }

    class Context(
        val callsToAddImplicitReceiver: List<CallToAddImplicitReceiver>,
        val thisExpressionsToAddLabels: List<ThisExpressionToAddLabel>,
        val project: Project,
    ) {
        fun isValid(): Boolean {
            return callsToAddImplicitReceiver.all { it.isValid() }
                    && thisExpressionsToAddLabels.all { it.isValid() }
        }

        fun allLabels(): List<String> {
            return callsToAddImplicitReceiver.map { it.labelToAdd }
        }

        class CallToAddImplicitReceiver(
            val call: SmartPsiElementPointer<KtExpression>,
            val labelToAdd: String,
        ) {
            fun isValid(): Boolean {
                return call.element != null
            }
        }

        class ThisExpressionToAddLabel(
            val thisExpression: SmartPsiElementPointer<KtThisExpression>,
            val labelToAdd: String,
        ) {
            fun isValid(): Boolean {
                return thisExpression.element != null
            }
        }
    }
}