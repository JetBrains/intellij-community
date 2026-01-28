// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.inspections.ExplicitThisInspection.Util.thisAsReceiverOrNull
import org.jetbrains.kotlin.idea.intentions.receiverType
import org.jetbrains.kotlin.idea.util.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyClassDescriptor
import org.jetbrains.kotlin.resolve.scopes.utils.getImplicitReceiversHierarchy
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

@K1Deprecation
class ExplicitThisInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
        override fun visitExpression(expression: KtExpression) {
            val thisExpression = expression.thisAsReceiverOrNull() ?: return
            if (Util.hasExplicitThis(expression)) {
                holder.registerProblem(
                    thisExpression,
                    KotlinBundle.message("redundant.explicit.this"),
                    ExplicitThisExpressionFix(thisExpression.text)
                )
            }
        }
    }

    object Util {
        fun KtExpression.thisAsReceiverOrNull(): KtThisExpression? = when (this) {
            is KtCallableReferenceExpression -> receiverExpression as? KtThisExpression
            is KtDotQualifiedExpression -> receiverExpression as? KtThisExpression
            else -> null
        }

        fun hasExplicitThis(expression: KtExpression): Boolean {
            val thisExpression = expression.thisAsReceiverOrNull() ?: return false
            val reference = when (expression) {
                is KtCallableReferenceExpression -> expression.callableReference
                is KtDotQualifiedExpression -> expression.selectorExpression as? KtReferenceExpression
                else -> null
            } ?: return false
            val referenceExpression = reference as? KtNameReferenceExpression ?: reference.getChildOfType() ?: return false

            val context = expression.analyze()
            if (context.diagnostics.forElement(referenceExpression).any { it.factory == Errors.UNRESOLVED_REFERENCE }) return false
            val scope = expression.getResolutionScope(context) ?: return false
            val receiverType = context[BindingContext.EXPRESSION_TYPE_INFO, thisExpression]?.type ?: return false

            val referenceTarget = context[BindingContext.REFERENCE_TARGET, reference.referenceExpression()]
            val name = referenceExpression.getReferencedNameAsName()
            val candidates = if (reference is KtCallExpression
                || (expression is KtCallableReferenceExpression && referenceTarget is FunctionDescriptor)
            ) {
                scope.getAllAccessibleFunctions(name) +
                        scope.getAllAccessibleVariables(name).filter { it is LocalVariableDescriptor && it.canInvoke() }
            } else {
                scope.getAllAccessibleVariables(name)
            }
            if (candidates.any { it.receiverType() == null }) return false

            val implicitThis = reference.getResolutionScope(context)?.getImplicitReceiversHierarchy()?.firstOrNull() ?: return false
            val explicitThis = thisExpression.getResolvedCall(context)?.resultingDescriptor ?: return false
            val candidate = (if (implicitThis == explicitThis) candidates.firstOrNull() else candidates.singleOrNull()) ?: return false

            val extensionType = candidate.extensionReceiverParameter?.type
            if (extensionType != null && extensionType != receiverType && receiverType.isSubtypeOf(extensionType)) return false

            val expressionFactory = scope.getFactoryForImplicitReceiverWithSubtypeOf(receiverType) ?: return false
            val label = thisExpression.getLabelName() ?: ""
            return expressionFactory.matchesLabel(label)
        }

        private fun VariableDescriptor.canInvoke(): Boolean {
            val declarationDescriptor = this.type.constructor.declarationDescriptor as? LazyClassDescriptor ?: return false
            return declarationDescriptor.declaredCallableMembers.any { (it as? FunctionDescriptor)?.isOperator == true }
        }

        private fun ReceiverExpressionFactory.matchesLabel(label: String): Boolean {
            val implicitLabel = expressionText.substringAfter("@", "")
            return label == implicitLabel || (label == "" && isImmediate)
        }
    }
}

@K1Deprecation
class ExplicitThisExpressionFix(private val text: String) : LocalQuickFix {
    override fun getFamilyName(): String = KotlinBundle.message("explicit.this.expression.fix.family.name", text)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val thisExpression = descriptor.psiElement as? KtThisExpression ?: return
        removeExplicitThisExpression(thisExpression)
    }

    companion object {
        fun removeExplicitThisExpression(thisExpression: KtThisExpression) {
            when (val parent = thisExpression.parent) {
                is KtDotQualifiedExpression -> parent.replace(parent.selectorExpression ?: return)
                is KtCallableReferenceExpression -> thisExpression.delete()
            }
        }
    }
}

