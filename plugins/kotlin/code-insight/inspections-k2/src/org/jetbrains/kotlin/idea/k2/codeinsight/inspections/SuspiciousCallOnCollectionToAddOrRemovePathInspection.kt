// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.SuspiciousCallOnCollectionToAddOrRemovePathInspection.Context
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

private val SUSPICIOUS_CALLABLE_IDS: Set<CallableId> = setOf(
    CallableId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, Name.identifier("plus")),
    CallableId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, Name.identifier("minus")),
    CallableId(StandardClassIds.BASE_SEQUENCES_PACKAGE, Name.identifier("plus")),
    CallableId(StandardClassIds.BASE_SEQUENCES_PACKAGE, Name.identifier("minus")),
)

/**
 * Note: 'plusAssign' / 'minusAssign' calls are not handled because of the compiler bug: KT-68963.
 */
internal class SuspiciousCallOnCollectionToAddOrRemovePathInspection : KotlinApplicableInspectionBase<KtExpression, Context>() {
    data class Context(val isPlus: Boolean)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = object : KtVisitorVoid() {
        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }

        override fun visitCallExpression(expression: KtCallExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtExpression): Boolean {
        return when (element) {
            is KtBinaryExpression -> {
                val left = element.left ?: return false
                val right = element.right ?: return false
                if (left is KtConstantExpression || right is KtConstantExpression) return false
                if (left is KtStringTemplateExpression || right is KtStringTemplateExpression) return false
                val op = element.operationToken
                op == KtTokens.PLUS || op == KtTokens.MINUS
            }

            is KtCallExpression -> {
                val argument = element.valueArguments.singleOrNull()?.getArgumentExpression() ?: return false
                val receiver = element.getQualifiedExpressionForSelector()?.receiverExpression ?: return false
                if (receiver is KtConstantExpression || argument is KtConstantExpression) return false
                if (receiver is KtStringTemplateExpression || argument is KtStringTemplateExpression) return false
                val calleeName = element.calleeExpression?.text ?: return false
                calleeName == "plus" || calleeName == "minus"
            }

            else -> false
        }
    }

    override fun KaSession.prepareContext(element: KtExpression): Context? {
        fun typeClassId(type: KaType?): ClassId? =
            when (type) {
                is KaFlexibleType -> type.upperBound
                else -> type
            }?.symbol?.classId

        fun getIterableElementTypeClassId(expression: KtExpression?): ClassId? {
            val type = expression?.expressionType ?: return null

            fun getElementClassId(classType: KaClassType): ClassId? {
                val elementType = classType.typeArguments.firstOrNull()?.type
                return typeClassId(elementType)
            }

            fun isIterableOrSequence(classId: ClassId?): Boolean {
                return classId == StandardClassIds.Iterable || classId == StandardClassIds.Sequence
            }

            val directClassId = typeClassId(type)
            if (isIterableOrSequence(directClassId)) {
                val classType = type as? KaClassType ?: return null
                return getElementClassId(classType)
            }
            val iterableSupertype = type.allSupertypes.firstOrNull {
                isIterableOrSequence(typeClassId(it))
            } as? KaClassType ?: return null
            return getElementClassId(iterableSupertype)
        }

        fun isSuspiciousIterable(leftExpression: KtExpression?, rightExpression: KtExpression?): Boolean {
            val rightClassId = typeClassId(rightExpression?.expressionType) ?: return false
            val iteratedClassId = getIterableElementTypeClassId(rightExpression) ?: return false
            val leftElementClassId = getIterableElementTypeClassId(leftExpression)

            fun isCollectionOrSequence(classId: ClassId?): Boolean {
                return classId == StandardClassIds.Collection || classId == StandardClassIds.Sequence
            }

            val rightType = rightExpression?.expressionType ?: return false
            val isRightACollection = isCollectionOrSequence(typeClassId(rightType)) || rightType.allSupertypes.any {
                isCollectionOrSequence(typeClassId(it))
            }

            return iteratedClassId == rightClassId ||
                    (iteratedClassId == leftElementClassId && !isRightACollection)
        }

        val isPlus = when (element) {
            is KtBinaryExpression -> {
                if (!isSuspiciousIterable(element.left, element.right)) return null
                element.operationToken == KtTokens.PLUS
            }

            is KtCallExpression -> {
                val receiver = element.getQualifiedExpressionForSelector()?.receiverExpression ?: return null
                val argument = element.valueArguments.singleOrNull()?.getArgumentExpression() ?: return null
                if (!isSuspiciousIterable(receiver, argument)) return null
                element.calleeExpression?.text == "plus"
            }

            else -> return null
        }

        val call = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        return if (call.symbol.callableId in SUSPICIOUS_CALLABLE_IDS) {
            Context(isPlus)
        } else {
            null
        }
    }

    override fun getApplicableRanges(element: KtExpression): List<TextRange> = when (element) {
        is KtBinaryExpression -> ApplicabilityRange.single(element) { it.operationReference }
        is KtCallExpression -> ApplicabilityRange.single(element) { it.calleeExpression ?: it }
        else -> emptyList()
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        val message = if (context.isPlus) {
            KotlinBundle.message("plus.call.appends.path.elements")
        } else {
            KotlinBundle.message("minus.call.removes.path.elements")
        }

        return createProblemDescriptor(
            /* psiElement = */ element,
            /* rangeInElement = */ rangeInElement,
            /* descriptionTemplate = */ message,
            /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            /* onTheFly = */ onTheFly,
            /* ...fixes = */ ConvertToElementCallFix(context.isPlus), ConvertPathToExplicitCollectionFix(context.isPlus)
        )
    }
}

private class ConvertToElementCallFix(private val isPlus: Boolean) : KotlinModCommandQuickFix<KtExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String =
        if (isPlus) KotlinBundle.message("convert.to.plus.element.call") else KotlinBundle.message("convert.to.minus.element.call")

    override fun applyFix(project: Project, element: KtExpression, updater: ModPsiUpdater) {
        val functionName = if (isPlus) "plusElement" else "minusElement"
        when (element) {
            is KtBinaryExpression -> {
                val left = element.left ?: return
                val right = element.right ?: return
                val newCall = KtPsiFactory(project).createExpressionByPattern("$0.$functionName($1)", left, right)
                element.replace(newCall)
            }

            is KtCallExpression -> {
                val newCallee = KtPsiFactory(project).createExpression(functionName)
                element.calleeExpression?.replace(newCallee)
            }
        }
    }
}

/**
 * For 'plus' calls, 'toList()' preserves the element order.
 * For 'minus' calls, order doesn't matter, so 'toSet()' is more efficient.
 */
private class ConvertPathToExplicitCollectionFix(private val isPlus: Boolean) :
    KotlinModCommandQuickFix<KtExpression>(), LowPriorityAction {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("convert.path.to.explicit.collection")

    override fun applyFix(project: Project, element: KtExpression, updater: ModPsiUpdater) {
        val functionName = if (isPlus) "toList" else "toSet"
        val factory = KtPsiFactory(project)
        when (element) {
            is KtBinaryExpression -> {
                val right = element.right ?: return
                val newCall = factory.createExpressionByPattern("$0.${functionName}()", right)
                right.replace(newCall)
            }

            is KtCallExpression -> {
                val argument = element.valueArguments.singleOrNull()?.getArgumentExpression() ?: return
                val newCall = factory.createExpressionByPattern("$0.${functionName}()", argument)
                argument.replace(newCall)
            }
        }
    }
}
