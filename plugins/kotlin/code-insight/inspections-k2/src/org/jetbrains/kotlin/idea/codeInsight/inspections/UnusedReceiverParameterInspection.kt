// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.expressions.expectedType
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.containingDeclaration
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.utils.getThisLabelName
import org.jetbrains.kotlin.idea.codeInsight.inspections.utils.getThisWithLabel
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.typeIfSafeToResolve
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ReceiverParameterChangeSignatureUtils
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ReceiverParameterChangeSignatureUtils.collectUsedTypeParameters
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ReceiverParameterChangeSignatureUtils.isReceiverUsedInside
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isOverridable
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier

internal class UnusedReceiverParameterInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitNamedFunction(function: KtNamedFunction) {
            checkElement(function, holder)
        }

        override fun visitProperty(property: KtProperty) {
            checkElement(property, holder)
        }
    }

    private fun registerProblem(
        holder: ProblemsHolder,
        receiverTypeReference: KtTypeReference,
        textForReceiver: String?
    ) {
        holder.registerProblem(
            receiverTypeReference,
            KotlinBundle.message("inspection.unused.receiver.parameter"),
            RemoveReceiverFix(textForReceiver)
        )
    }

    private fun checkElement(callableDeclaration: KtCallableDeclaration, holder: ProblemsHolder) {
        val receiverTypeReference = callableDeclaration.receiverTypeReference
        if (receiverTypeReference == null || receiverTypeReference.textRange.isEmpty) return

        if (callableDeclaration is KtProperty && callableDeclaration.accessors.isEmpty()) return
        if (callableDeclaration is KtNamedFunction) {
            if (!callableDeclaration.hasBody()) return
            if (callableDeclaration.name == null) {
                val parentQualified = callableDeclaration.getStrictParentOfType<KtQualifiedExpression>()
                if (KtPsiUtil.deparenthesize(parentQualified?.callExpression?.calleeExpression) == callableDeclaration) return
            }
        }

        if (callableDeclaration.hasModifier(KtTokens.OVERRIDE_KEYWORD) ||
            callableDeclaration.hasModifier(KtTokens.OPERATOR_KEYWORD) ||
            callableDeclaration.hasModifier(KtTokens.INFIX_KEYWORD) ||
            callableDeclaration.hasActualModifier() ||
            callableDeclaration.isOverridable() ||
            (callableDeclaration is KtProperty && callableDeclaration.delegate != null)
        ) return

        analyze(callableDeclaration) {
            if (callableDeclaration.expectedType != null) return

            val usedTypeParametersInReceiver = collectUsedTypeParameters(receiverTypeReference)
            val usedInReturnType = callableDeclaration.typeReference
                ?.let { collectUsedTypeParameters(it) }
                .orEmpty()
            if (usedTypeParametersInReceiver.any { it in usedInReturnType }) return

            val receiverType = receiverTypeReference.typeIfSafeToResolve
            val receiverTypeSymbol = receiverType?.symbol
            if (receiverTypeSymbol is KaClassSymbol && receiverTypeSymbol.classKind == KaClassKind.COMPANION_OBJECT) return

            val callableSymbol = callableDeclaration.symbol

            val containingDeclarationSymbol = callableSymbol.containingDeclaration
            if (containingDeclarationSymbol != null && containingDeclarationSymbol == receiverTypeSymbol) {
                val thisLabelName = containingDeclarationSymbol.getThisLabelName()
                val thisLabelNamesInCallable =
                    callableDeclaration.collectDescendantsOfType<KtThisExpression>().mapNotNull { it.getLabelName() }
                if (thisLabelNamesInCallable.isNotEmpty()) {
                    if (thisLabelNamesInCallable.none { it == thisLabelName }) {
                        registerProblem(holder, receiverTypeReference, callableSymbol.getThisWithLabel())
                    }
                    return
                }
            }

            val usedReifiedTypeParametersInReceiver = usedTypeParametersInReceiver.filterTo(mutableSetOf()) { it.isReified }
            val receiverUsedInside = isReceiverUsedInside(callableDeclaration, usedReifiedTypeParametersInReceiver)
            if (!receiverUsedInside) {
                registerProblem(holder, receiverTypeReference, textForReceiver = null)
            }
        }
    }

    private class RemoveReceiverFix(private val textForReceiver: String?) : LocalQuickFix {
        override fun getFamilyName(): String =
            KotlinBundle.message("fix.unused.receiver.parameter.remove")

        override fun startInWriteAction(): Boolean = false


        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as? KtTypeReference ?: return
            if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return
            val function = element.parent as? KtCallableDeclaration ?: return

            ReceiverParameterChangeSignatureUtils.removeReceiverParameter(project, function, textForReceiver)
        }
    }
}
