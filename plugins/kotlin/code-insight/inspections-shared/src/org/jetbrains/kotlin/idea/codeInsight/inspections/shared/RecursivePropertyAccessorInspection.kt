// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.RecursivePropertyAccessorInspection.Context
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

class RecursivePropertyAccessorInspection : KotlinApplicableInspectionBase<KtSimpleNameExpression, Context>() {
    enum class Context { RECURSIVE_PROPERTY_ACCESS, RECURSIVE_SYNTHETIC_PROPERTY_ACCESS }

    private fun getProblemDescription(context: Context): @InspectionMessage String = when (context) {
        Context.RECURSIVE_PROPERTY_ACCESS -> KotlinBundle.message("recursive.property.accessor")
        Context.RECURSIVE_SYNTHETIC_PROPERTY_ACCESS -> KotlinBundle.message("recursive.synthetic.property.accessor")
    }

    private fun createQuickFix(element: KtSimpleNameExpression): KotlinModCommandQuickFix<KtSimpleNameExpression>? {
        // Skip if the property is an extension property.
        val isExtensionProperty = element.getStrictParentOfType<KtProperty>()?.receiverTypeReference != null
        if (isExtensionProperty) return null

        return object : KotlinModCommandQuickFix<KtSimpleNameExpression>() {
            override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.with.field.fix.text")

            override fun applyFix(
                project: Project, element: KtSimpleNameExpression, updater: ModPsiUpdater,
            ) {
                val factory = KtPsiFactory(project)
                element.replace(factory.createExpression("field"))
            }
        }
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtSimpleNameExpression, context: Context, rangeInElement: TextRange?, onTheFly: Boolean
    ): ProblemDescriptor = createQuickFix(element)?.let {
        createProblemDescriptor(
            /* psiElement = */ element,
            /* rangeInElement = */ rangeInElement,
            /* descriptionTemplate = */ getProblemDescription(context),
            /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            /* onTheFly = */ onTheFly,
            /* ...fixes = */ it,
        )
    } ?: createProblemDescriptor(
        /* psiElement = */ element,
        /* rangeInElement = */ rangeInElement,
        /* descriptionTemplate = */ getProblemDescription(context),
        /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        /* onTheFly = */ onTheFly,
    )

    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
    ): KtVisitor<*, *> = simpleNameExpressionVisitor { visitTargetElement(it, holder, isOnTheFly) }

    override fun isApplicableByPsi(element: KtSimpleNameExpression): Boolean {
        return isApplicablePropertyAccessPsi(element) || isApplicableSyntheticPropertyAccessPsi(element)
    }

    override fun KaSession.prepareContext(element: KtSimpleNameExpression): Context? {
        if (element.isRecursivePropertyAccess(false)) return Context.RECURSIVE_PROPERTY_ACCESS
        if (isRecursiveSyntheticPropertyAccess(element)) return Context.RECURSIVE_SYNTHETIC_PROPERTY_ACCESS
        return null
    }

    companion object {
        @ApiStatus.ScheduledForRemoval
        @Deprecated(
            "use isRecursivePropertyAccess(KtElement, Boolean) instead",
            replaceWith = ReplaceWith("isRecursivePropertyAccess(element, false)")
        )
        fun isRecursivePropertyAccess(element: KtElement): Boolean = isRecursivePropertyAccess(element, false)

        fun isRecursivePropertyAccess(element: KtElement, anyRecursionTypes: Boolean): Boolean {
            if (element !is KtSimpleNameExpression) return false
            return isApplicablePropertyAccessPsi(element) && element.isRecursivePropertyAccess(anyRecursionTypes)
        }

        fun isRecursiveSyntheticPropertyAccess(element: KtElement): Boolean {
            if (element !is KtSimpleNameExpression) return false
            return isApplicableSyntheticPropertyAccessPsi(element) && element.isRecursiveSyntheticPropertyAccess()
        }

        private fun isApplicablePropertyAccessPsi(element: KtSimpleNameExpression): Boolean {
            val propertyAccessor = element.getParentOfType<KtDeclarationWithBody>(true) as? KtPropertyAccessor ?: return false
            if (element.text != propertyAccessor.property.name) return false
            return element.parent !is KtCallableReferenceExpression
        }

        private fun isApplicableSyntheticPropertyAccessPsi(element: KtSimpleNameExpression): Boolean {
            val namedFunction = element.getParentOfType<KtDeclarationWithBody>(true) as? KtNamedFunction ?: return false
            val name = namedFunction.name ?: return false
            val referencedName = element.text.capitalizeAsciiOnly()
            val isGetter = name == "get$referencedName"
            val isSetter = name == "set$referencedName"
            if (!isGetter && !isSetter) return false
            return element.parent !is KtCallableReferenceExpression
        }

        private fun KaSession.hasThisAsReceiver(expression: KtQualifiedExpression) =
            expression.receiverExpression.textMatches(KtTokens.THIS_KEYWORD.value)

        private fun KaSession.hasObjectReceiver(expression: KtQualifiedExpression): Boolean {
            val receiver = expression.receiverExpression as? KtReferenceExpression ?: return false
            val receiverAsClassSymbol = receiver.resolveExpression() as? KaClassSymbol ?: return false
            return receiverAsClassSymbol.classKind.isObject
        }

        private fun KtBinaryExpression?.isAssignmentTo(expression: KtSimpleNameExpression): Boolean =
            this != null && KtPsiUtil.isAssignment(this) && PsiTreeUtil.isAncestor(left, expression, false)

        private fun isSameAccessor(expression: KtSimpleNameExpression, isGetter: Boolean): Boolean {
            val binaryExpr = expression.getStrictParentOfType<KtBinaryExpression>()
            if (isGetter) {
                if (binaryExpr.isAssignmentTo(expression)) {
                    return KtTokens.AUGMENTED_ASSIGNMENTS.contains(binaryExpr?.operationToken)
                }
                return true
            } else /* isSetter */ {
                if (binaryExpr.isAssignmentTo(expression)) {
                    return true
                }
                val unaryExpr = expression.getStrictParentOfType<KtUnaryExpression>()
                if (unaryExpr?.operationToken.let { it == KtTokens.PLUSPLUS || it == KtTokens.MINUSMINUS }) {
                    return true
                }
            }
            return false
        }

        private fun KtSimpleNameExpression.isRecursivePropertyAccess(anyRecursionTypes: Boolean): Boolean {
            val propertyAccessor = getParentOfType<KtDeclarationWithBody>(true) as? KtPropertyAccessor ?: return false
            analyze(this) {
                val propertySymbol = resolveToCall()?.successfulVariableAccessCall()?.symbol as? KaPropertySymbol ?: return false
                if (propertySymbol != propertyAccessor.property.symbol) return false

                (parent as? KtQualifiedExpression)?.let {
                    val propertyReceiverType = propertySymbol.receiverType
                    val receiverType = it.receiverExpression.expressionType?.withNullability(KaTypeNullability.NON_NULLABLE)
                    if (anyRecursionTypes) {
                        if (receiverType != null && propertyReceiverType != null && !receiverType.isSubtypeOf(propertyReceiverType)) {
                            return false
                        }
                    } else {
                        if (!hasThisAsReceiver(it) && !hasObjectReceiver(it) && (propertyReceiverType == null || receiverType?.isSubtypeOf(
                                propertyReceiverType
                            ) == false)
                        ) {
                            return false
                        }
                    }
                }
            }
            return isSameAccessor(this, propertyAccessor.isGetter)
        }

        private fun KtSimpleNameExpression.isRecursiveSyntheticPropertyAccess(): Boolean {
            val namedFunction = getParentOfType<KtDeclarationWithBody>(true) as? KtNamedFunction ?: return false

            analyze(this) {
                val syntheticSymbol = mainReference.resolveToSymbol() as? KaSyntheticJavaPropertySymbol ?: return false
                val namedFunctionSymbol = namedFunction.symbol
                if (namedFunctionSymbol != syntheticSymbol.javaGetterSymbol && namedFunctionSymbol != syntheticSymbol.javaSetterSymbol) {
                    return false
                }
            }

            val name = namedFunction.name ?: return false
            val referencedName = text.capitalizeAsciiOnly()
            val isGetter = name == "get$referencedName"
            return isSameAccessor(this, isGetter)
        }
    }
}
