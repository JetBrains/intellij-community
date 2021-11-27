// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ValueDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.idea.intentions.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingContext.REFERENCE_TARGET
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getImplicitReceiverValue
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ReplaceWithIsNullOrEmptyInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = binaryExpressionVisitor(fun(expression: KtBinaryExpression) {
        val operationToken = expression.operationToken
        val isOr = operationToken == KtTokens.OROR
        val isAnd = operationToken == KtTokens.ANDAND
        if (!isOr && !isAnd) return

        val right = expression.rightExpression()?.first ?: return
        val rightCallText =
            (right.safeAs<KtCallExpression>() ?: right.safeAs<KtQualifiedExpression>()?.callExpression)?.calleeExpression?.text

        val checkedExpressionInLeft = expression.getCheckedExpressionInLeft(isOr) ?: return
        val context = expression.analyze(BodyResolveMode.PARTIAL)
        val checkedDeclarationInLeft = when (checkedExpressionInLeft) {
            is KtThisExpression -> checkedExpressionInLeft.getResolvedCall(context)?.resultingDescriptor?.containingDeclaration
            is KtSimpleNameExpression -> context[REFERENCE_TARGET, checkedExpressionInLeft]
            else -> null
        } ?: return

        val checkedDeclarationInRight = expression.getCheckedDeclarationInRight(isOr, context)
        if (checkedDeclarationInLeft != checkedDeclarationInRight) return

        val functionName = if (rightCallText == "isBlank" || rightCallText == "isNotBlank") "isNullOrBlank" else "isNullOrEmpty"
        if (functionName == expression.getStrictParentOfType<KtFunction>()?.name) return
        holder.registerProblem(expression, KotlinBundle.message("replace.with.0.call", functionName), ReplaceFix(functionName, isOr))
    })

    private class ReplaceFix(private val functionName: String, private val isOr: Boolean) : LocalQuickFix {
        override fun getFamilyName() = KotlinBundle.message("replace.with.0.call", functionName)

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val expression = descriptor.psiElement as? KtBinaryExpression ?: return
            val checkedExpressionInLeft = expression.getCheckedExpressionInLeft(isOr) ?: return
            val receiver = if (checkedExpressionInLeft is KtThisExpression) "" else "${checkedExpressionInLeft.text}."
            val negated = if (isOr) "" else "!"
            expression.replace(KtPsiFactory(project).createExpression("$negated$receiver$functionName()"))
        }
    }

    companion object {
        private val collectionClasses = listOf(
            StandardNames.FqNames.list,
            StandardNames.FqNames.set,
            StandardNames.FqNames.collection,
            StandardNames.FqNames.map,
            StandardNames.FqNames.mutableList,
            StandardNames.FqNames.mutableSet,
            StandardNames.FqNames.mutableCollection,
            StandardNames.FqNames.mutableMap,
        )

        private val emptyCheckFunctions = collectionClasses.map { FqName("$it.isEmpty") } +
                listOf("kotlin.collections.isEmpty", "kotlin.text.isEmpty", "kotlin.text.isBlank").map(::FqName)

        private val notEmptyCheckFunctions = collectionClasses.map { FqName("$it.isNotEmpty") } +
                listOf("kotlin.collections.isNotEmpty", "kotlin.text.isNotEmpty", "kotlin.text.isNotBlank").map(::FqName)

        private fun KtBinaryExpression.getCheckedExpressionInLeft(isOr: Boolean): KtExpression? {
            val checkedExpression = left.safeAs<KtBinaryExpression>()
                ?.takeIf { it.operationToken == if (isOr) KtTokens.EQEQ else KtTokens.EXCLEQ }
                ?.let {
                    when {
                        it.left?.text == KtTokens.NULL_KEYWORD.value -> it.right
                        it.right?.text == KtTokens.NULL_KEYWORD.value -> it.left
                        else -> null
                    }
                } ?: return null
            return checkedExpression.safeAs<KtSimpleNameExpression>() ?: checkedExpression.safeAs<KtThisExpression>()
        }

        private fun KtBinaryExpression.getCheckedDeclarationInRight(isOr: Boolean, context: BindingContext): DeclarationDescriptor? {
            val (right, negated) = rightExpression() ?: return null
            val emptyCheckFunctions = if (isOr) {
                if (!negated) emptyCheckFunctions else notEmptyCheckFunctions
            } else {
                if (!negated) notEmptyCheckFunctions else emptyCheckFunctions
            }
            val (declaration, declarationType) = when (right) {
                is KtBinaryExpression -> {
                    val checkedExpression = if (isOr) {
                        ReplaceSizeZeroCheckWithIsEmptyIntention.getCheckedExpression(right)
                    } else {
                        ReplaceSizeCheckWithIsNotEmptyIntention.getCheckedExpression(right)
                    }?.takeIf { it.isSizeOrLength(context) || it.isCountCall { call -> call.valueArguments.isEmpty() } }
                    checkedExpression.safeAs<KtDotQualifiedExpression>()?.receiverReference(context)
                        ?: checkedExpression?.getResolvedCall(context)?.implicitReceiverReference()
                }
                is KtDotQualifiedExpression -> {
                    if (right.callExpression?.isCalling(emptyCheckFunctions, context) == true) right.receiverReference(context) else null
                }
                is KtCallExpression -> {
                    val calleeText = right.calleeExpression?.text
                    if (emptyCheckFunctions.any { it.shortName().asString() == calleeText }) {
                        val resolvedCall = right.getResolvedCall(context)
                        val fqName = resolvedCall?.resultingDescriptor?.fqNameSafe
                        if (fqName in emptyCheckFunctions) resolvedCall?.implicitReceiverReference() else null
                    } else {
                        null
                    }
                }
                else -> null
            } ?: return null
            if (declarationType == null || KotlinBuiltIns.isPrimitiveArray(declarationType)) return null
            return declaration
        }

        private fun KtBinaryExpression.rightExpression(): Pair<KtExpression, Boolean>? {
            val right = this.right ?: return null
            return if (right is KtPrefixExpression) {
                val base = right.baseExpression
                if (base != null && right.operationToken == KtTokens.EXCL) base to true else null
            } else {
                right to false
            }
        }

        private fun KtDotQualifiedExpression.receiverReference(context: BindingContext): Pair<DeclarationDescriptor?, KotlinType?> {
            val receiver = receiverExpression as? KtSimpleNameExpression
            val declaration = context[REFERENCE_TARGET, receiver]
            val declarationType = (declaration as? ValueDescriptor)?.type
            return declaration to declarationType
        }

        private fun ResolvedCall<out CallableDescriptor>.implicitReceiverReference(): Pair<DeclarationDescriptor?, KotlinType?> {
            val implicitReceiverValue = getImplicitReceiverValue()
            val declaration = implicitReceiverValue?.declarationDescriptor
            val declarationType = implicitReceiverValue?.type
            return declaration to declarationType
        }
    }
}
