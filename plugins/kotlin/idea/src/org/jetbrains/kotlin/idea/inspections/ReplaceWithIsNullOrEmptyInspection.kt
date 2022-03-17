// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ReceiverParameterDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi2ir.deparenthesize
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.findOriginalTopMostOverriddenDescriptors
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.isError

class ReplaceWithIsNullOrEmptyInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = binaryExpressionVisitor(fun(binaryExpression) {
        val nullCheckExpression = binaryExpression.left?.deparenthesize() as? KtExpression ?: return
        val nullCheck = getNullCheck(nullCheckExpression) ?: return

        val operationToken = binaryExpression.operationToken

        val isPositiveCheck = when {
            !nullCheck.isEqualNull && operationToken == KtTokens.ANDAND -> true // a != null && a.isNotEmpty()
            nullCheck.isEqualNull && operationToken == KtTokens.OROR -> false // a == null || a.isEmpty()
            else -> return
        }

        val contentCheckExpression = binaryExpression.right?.deparenthesize() as? KtExpression ?: return
        val contentCheck = getContentCheck(contentCheckExpression) ?: return

        if (isPositiveCheck != contentCheck.isPositiveCheck) return
        if (!isSimilar(nullCheck.target, contentCheck.target, ::psiOnlyTargetCheck)) return

        val bindingContext = binaryExpression.analyze(BodyResolveMode.PARTIAL)
        if (!isSimilar(nullCheck.target, contentCheck.target, resolutionTargetCheckFactory(bindingContext))) return

        // Lack of smart-cast in 'a != null && <<a>>.isNotEmpty()' means it's rather some complex expression. Skip those for now
        if (!contentCheck.target.last().hasSmartCast(bindingContext)) return

        val contentCheckCall = contentCheck.call.getResolvedCall(bindingContext) ?: return
        val contentCheckFunction = contentCheckCall.resultingDescriptor as? SimpleFunctionDescriptor ?: return
        if (!checkTargetFunctionReceiver(contentCheckFunction)) return

        val overriddenFunctions = contentCheckFunction.findOriginalTopMostOverriddenDescriptors()
        if (overriddenFunctions.none { it.fqNameOrNull()?.asString() in contentCheck.data.callableNames }) return

        val hasExplicitReceiver = contentCheck.target.singleOrNull() !is TargetChunk.ImplicitThis
        val replacementName = contentCheck.data.replacementName

        holder.registerProblem(
            binaryExpression,
            KotlinBundle.message("replace.with.0.call", (if (isPositiveCheck) "!" else "") + replacementName),
            ReplaceFix(replacementName, hasExplicitReceiver, isPositiveCheck)
        )
    })

    private fun checkTargetFunctionReceiver(function: SimpleFunctionDescriptor): Boolean {
        val type = getSingleReceiver(function.dispatchReceiverParameter?.value, function.extensionReceiverParameter?.value)?.type
        return type != null && !type.isError && !type.isMarkedNullable && !KotlinBuiltIns.isPrimitiveArray(type)
    }

    private fun psiOnlyTargetCheck(left: TargetChunk, right: TargetChunk) = left.kind == right.kind && left.name == right.name

    private fun resolutionTargetCheckFactory(bindingContext: BindingContext): (TargetChunk, TargetChunk) -> Boolean = r@ { left, right ->
        val leftValue = left.resolve(bindingContext) ?: return@r false
        val rightValue = right.resolve(bindingContext) ?: return@r false
        return@r leftValue == rightValue
    }

    private fun getTargetChain(targetExpression: KtExpression): TargetChain? {
        val result = mutableListOf<TargetChunk>()

        fun processDepthFirst(expression: KtExpression): Boolean {
            when (val unwrapped = expression.deparenthesize()) {
                is KtNameReferenceExpression -> result += TargetChunk.Name(unwrapped)
                is KtDotQualifiedExpression -> {
                    if (!processDepthFirst(unwrapped.receiverExpression)) return false
                    val selectorExpression = unwrapped.selectorExpression as? KtNameReferenceExpression ?: return false
                    result += TargetChunk.Name(selectorExpression)
                }
                is KtThisExpression -> result += TargetChunk.ExplicitThis(unwrapped)
                else -> return false
            }

            return true
        }

        return if (processDepthFirst(targetExpression)) result.takeIf { it.isNotEmpty() } else null
    }

    private fun isSimilar(left: TargetChain, right: TargetChain, checker: (TargetChunk, TargetChunk) -> Boolean): Boolean {
        return left.size == right.size && left.indices.all { index -> checker(left[index], right[index]) }
    }

    private fun <T: Any> unwrapNegation(expression: KtExpression, block: (KtExpression, Boolean) -> T?): T? {
        if (expression is KtPrefixExpression && expression.operationToken == KtTokens.EXCL) {
            val baseExpression = expression.baseExpression?.deparenthesize() as? KtExpression ?: return null
            return block(baseExpression, true)
        }

        return block(expression, false)
    }

    private class NullCheck(val target: TargetChain, val isEqualNull: Boolean)

    private fun getNullCheck(expression: KtExpression) = unwrapNegation(expression, fun(expression, isNegated): NullCheck? {
        if (expression !is KtBinaryExpression) return null

        val isNull = when (expression.operationToken) {
            KtTokens.EQEQ -> true
            KtTokens.EXCLEQ -> false
            else -> return null
        } xor isNegated

        val left = expression.left ?: return null
        val right = expression.right ?: return null

        fun createTarget(targetExpression: KtExpression, isNull: Boolean): NullCheck? {
            val targetReferenceExpression = getTargetChain(targetExpression) ?: return null
            return NullCheck(targetReferenceExpression, isNull)
        }

        return when {
            left.isNullLiteral() -> createTarget(right, isNull)
            right.isNullLiteral() -> createTarget(left, isNull)
            else -> null
        }
    })

    private class ContentCheck(val target: TargetChain, val call: KtCallExpression, val data: ContentFunction, val isPositiveCheck: Boolean)

    private fun getContentCheck(expression: KtExpression) = unwrapNegation(expression, fun(expression, isNegated): ContentCheck? {
        val (callExpression, target) = when (expression) {
            is KtCallExpression -> expression to listOf(TargetChunk.ImplicitThis(expression))
            is KtDotQualifiedExpression -> {
                val targetChain = getTargetChain(expression.receiverExpression) ?: return null
                (expression.selectorExpression as? KtCallExpression) to targetChain
            }
            else -> return null
        }

        val calleeText = callExpression?.calleeExpression?.text ?: return null
        val contentFunction = contentCheckingFunctions[calleeText] ?: return null
        return ContentCheck(target, callExpression, contentFunction, contentFunction.isPositiveCheck xor isNegated)
    })

    private class ReplaceFix(
        private val functionName: String,
        private val hasExplicitReceiver: Boolean,
        private val isPositiveCheck: Boolean
    ) : LocalQuickFix {
        override fun getFamilyName() = KotlinBundle.message("replace.with.0.call", functionName)

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val binaryExpression = descriptor.psiElement as? KtBinaryExpression ?: return
            val expressionText = buildString {
                if (isPositiveCheck) append("!")
                if (hasExplicitReceiver) {
                    val nullCheckExpression = binaryExpression.left?.deparenthesize() as? KtExpression ?: return
                    val receiverExpression = getReceiver(nullCheckExpression) ?: return
                    append(receiverExpression.text).append(".")
                }
                append(functionName).append("()")
            }
            binaryExpression.replace(KtPsiFactory(project).createExpression(expressionText))
        }

        private fun getReceiver(expression: KtExpression?): KtExpression? = when {
            expression is KtPrefixExpression && expression.operationToken == KtTokens.EXCL -> getReceiver(expression.baseExpression)
            expression is KtBinaryExpression -> when {
                expression.left?.isNullLiteral() == true -> expression.right
                expression.right?.isNullLiteral() == true -> expression.left
                else -> null
            }
            else -> null
        }
    }

    private class ContentFunction(val isPositiveCheck: Boolean, val replacementName: String, vararg val callableNames: String)

    companion object {
        private val contentCheckingFunctions = mapOf(
            "isEmpty" to ContentFunction(
                isPositiveCheck = false, replacementName = "isNullOrEmpty",
                "kotlin.collections.Collection.isEmpty",
                "kotlin.collections.Map.isEmpty",
                "kotlin.collections.isEmpty",
                "kotlin.text.isEmpty"
            ),
            "isBlank" to ContentFunction(
                isPositiveCheck = false, replacementName = "isNullOrBlank",
                "kotlin.text.isBlank"
            ),
            "isNotEmpty" to ContentFunction(
                isPositiveCheck = true, replacementName = "isNullOrEmpty",
                "kotlin.collections.isNotEmpty",
                "kotlin.text.isNotEmpty"
            ),
            "isNotBlank" to ContentFunction(
                isPositiveCheck = true, replacementName = "isNullOrBlank",
                "kotlin.text.isNotBlank"
            )
        )

        private fun KtExpression.isNullLiteral(): Boolean =
            (deparenthesize() as? KtConstantExpression)?.text == KtTokens.NULL_KEYWORD.value
    }
}

private typealias TargetChain = List<TargetChunk>

private sealed class TargetChunk(val kind: Kind) {
    enum class Kind { THIS, NAME }

    class ExplicitThis(val expression: KtThisExpression) : TargetChunk(Kind.THIS) {
        override val name: String?
            get() = null

        override fun resolve(bindingContext: BindingContext): Any? {
            val descriptor = expression.getResolvedCall(bindingContext)?.resultingDescriptor
            return (descriptor as? ReceiverParameterDescriptor)?.value ?: descriptor
        }

        override fun hasSmartCast(bindingContext: BindingContext): Boolean {
            return bindingContext[BindingContext.SMARTCAST, expression] != null
        }
    }

    class ImplicitThis(val expression: KtCallExpression) : TargetChunk(Kind.THIS) {
        override val name: String?
            get() = null

        override fun resolve(bindingContext: BindingContext): Any? {
            val resolvedCall = expression.getResolvedCall(bindingContext) ?: return null
            return getSingleReceiver(resolvedCall.dispatchReceiver, resolvedCall.extensionReceiver)
        }

        override fun hasSmartCast(bindingContext: BindingContext): Boolean {
            return bindingContext[BindingContext.IMPLICIT_RECEIVER_SMARTCAST, expression.calleeExpression] != null
        }
    }

    class Name(val expression: KtNameReferenceExpression) : TargetChunk(Kind.NAME) {
        override val name: String
            get() = expression.getReferencedName()

        override fun resolve(bindingContext: BindingContext) = bindingContext[BindingContext.REFERENCE_TARGET, expression]

        override fun hasSmartCast(bindingContext: BindingContext): Boolean {
            return expression.getQualifiedExpressionForSelectorOrThis().parenthesize()
                .any { bindingContext[BindingContext.SMARTCAST, it] != null }
        }
    }

    abstract val name: String?
    abstract fun resolve(bindingContext: BindingContext): Any?
    abstract fun hasSmartCast(bindingContext: BindingContext): Boolean
}

private fun getSingleReceiver(dispatchReceiver: ReceiverValue?, extensionReceiver: ReceiverValue?): ReceiverValue? {
    if (dispatchReceiver != null && extensionReceiver != null) {
        // Sic! Functions such as 'isEmpty()' never have both dispatch and extension receivers
        return null
    }

    return dispatchReceiver ?: extensionReceiver
}

private fun KtExpression.parenthesize(): Sequence<KtExpression> = sequence {
    var current = this@parenthesize
    while (true) {
        yield(current)
        current = (current.parent as? KtExpression)?.takeIf { KtPsiUtil.deparenthesizeOnce(it) === current } ?: break
    }
}