// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.core.receiverType
import org.jetbrains.kotlin.idea.inspections.dfa.getKotlinType
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.isConstant
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Converts the argument to `Set` for functions that call `contains` on their `Iterable`/`Array` argument.
 *
 * Functions like `Iterable.minus` or `Iterable.subtract` invoke the `contains` function on their argument.
 * This call if efficient when the argument is a `Set` (it may be true for other collections as well),
 * but it is not the case for `Array`, `ArrayList`, or `Sequence`. It may be useful to convert these
 * arguments to `Set` before calling `minus` and other functions with similar behavior.
 *
 * This intention detects cases where the conversion of the argument to `Set` may improve performance,
 * and adds the `toSet()` call to the argument.
 *
 * As an exception, direct invocation of `listOf`, `arrayOf`, `sequenceOf` and so on with constant arguments
 * are excluded from conversion, as they are usually small, and their conversion does not affect
 * the performance.
 *
 * In some cases there would be no performance gains from the conversion, but it should have no significant
 * negative effects as well.
 *
 * Note: this intention is a part of preparations to Kotlin stdlib changes:
 * https://youtrack.jetbrains.com/issue/KTIJ-19106
 *
 * @see org.jetbrains.kotlin.idea.inspections.ConvertArgumentToSetInspection for a matching inspection
 *
 */
class ConvertArgumentToSetIntention : SelfTargetingIntention<KtExpression>(
    KtExpression::class.java,
    KotlinBundle.messagePointer("convert.argument.to.set.fix.text")
), LowPriorityAction {

    override fun isApplicableTo(element: KtExpression, caretOffset: Int): Boolean = when (element) {
        is KtCallExpression -> Holder.isApplicableCall(element)
        is KtBinaryExpression -> Holder.isApplicableBinary(element)
        else -> false
    }

    override fun applyTo(element: KtExpression, editor: Editor?) {
        val arguments = Holder.getConvertibleArguments(element)
        for (arg in arguments) {
            arg.replace(KtPsiFactory(element.project).createExpressionByPattern("$0.toSet()", arg))
        }
    }


    // Implementation for the intention and the related inspection
    // Unit rests: org.jetbrains.kotlin.idea.intentions.IntentionTestGenerated.ConvertArgumentToSet
    // Unit tests: org.jetbrains.kotlin.idea.inspections.ConvertToSetInspectionTest
    object Holder {

        /**
         * Compute the list of arguments for which the conversion is recommended.
         *
         * This list will usually contain zero or one argument, as affected functions have the single
         * argument.
         *
         * @param element the PSI element to analyze
         */
        fun getConvertibleArguments(element: KtExpression): List<KtExpression> = when (element) {
            is KtCallExpression -> getConvertibleCallArguments(element)
            is KtBinaryExpression -> getConvertibleBinaryArguments(element)
            else -> emptyList()
        }

        internal fun isApplicableCall(element: KtCallExpression): Boolean =
            getConvertibleCallArguments(element).isNotEmpty()

        private fun getConvertibleCallArguments(element: KtCallExpression): List<KtExpression> {
            val receiverType = element.receiverType() ?: return emptyList()
            val calleeNameExpression = element.calleeExpression.safeAs<KtNameReferenceExpression>() ?: return emptyList()
            val candidateFunction = CandidateFunction(
                calleeNameExpression.getReferencedName(),
                resolveName(calleeNameExpression)?.safeAs<KtNamedFunction>()?.fqName?.asString()
            )

            return filterConvertibleArguments(
                candidateFunction,
                receiverType,
                element.valueArguments.mapNotNull { it.getArgumentExpression() }
            )
        }

        internal fun isApplicableBinary(element: KtBinaryExpression): Boolean =
            getConvertibleBinaryArguments(element).isNotEmpty()

        private fun getConvertibleBinaryArguments(element: KtBinaryExpression): List<KtExpression> {
            val argument = element.right ?: return emptyList()
            val receiver = element.left ?: return emptyList()
            val receiverType = receiver.getKotlinType() ?: return emptyList()

            val candidateFunction = when (element.operationToken) {
                KtTokens.MINUS -> CandidateFunction("minus", null)
                KtTokens.MINUSEQ -> CandidateFunction("minusAssign", null)
                KtTokens.IDENTIFIER -> CandidateFunction(
                    element.operationReference.getReferencedName(),
                    resolveName(element.operationReference)?.safeAs<KtNamedFunction>()?.fqName?.asString()
                )
                else -> null
            } ?: return emptyList()

            return filterConvertibleArguments(candidateFunction, receiverType, listOf(argument))
        }

        /**
         * For a call expression, check if the function being called is relevant,
         * and get arguments that should be converted.
         *
         * @param candidateFunction the possibly resolved function
         * @param receiverType the type of the receiver for which the function is called
         * @param arguments the list of function arguments
         *
         * @return the list of arguments that could be converted (an empty list if the conversion is not applicable)
         */

        private fun filterConvertibleArguments(
            candidateFunction: CandidateFunction,
            receiverType: KotlinType,
            arguments: List<KtExpression>
        ): List<KtExpression> {
            // Check that the callee name is not shadowed
            if (candidateFunction.isResolved && candidateFunction.resolvedName !in relevantStdlibFunctions)
                return emptyList()

            val compatibleReceivers = compatibleReceiverTypes[candidateFunction.name] ?: emptySet()
            return if (receiverType.hasMatchingSupertype { it.fqName?.asString() in compatibleReceivers })
                arguments.filter { canConvertArgument(it) }
            else
                emptyList()
        }

        /**
         * Check if we should propose the conversion for the argument.
         *
         * @param argument the candidate argument
         */
        private fun canConvertArgument(argument: KtExpression): Boolean {
            val argumentType = argument.getKotlinType() ?: return false
            val argumentTypeName = argumentType.fqName?.asString() ?: return false

            // Functions that we are dealing with don't work with nullable types
            if (argumentType.isMarkedNullable) return false

            // Do not suggest the fix if the argument was constructed using `arrayOf`, `listOf`, `sequenceOf` etc
            if (isLikeConstantExpressionListOf(argument)) return false

            // Suggest the fix if the argument has exact type that we know is convertible
            // (see the comment for the `alwaysConvertibleExactTypes` set for details)
            if (argumentTypeName in alwaysConvertibleExactTypes) return true

            // Suggest the fix if the argument is a `Sequence` (sequences are always converted)
            // In all other cases we don't suggest the fix
            // Note: there is no explicit check for `Set` subtypes, as it matches with our
            // "don't suggest the fix by default" approach
            return argumentType.hasMatchingSupertype { it.fqName?.asString() == "kotlin.sequences.Sequence" }
        }

        /**
         * Check if an expression is a call to `listOf`/`arrayOf`/`sequenceOf` etc. with constant arguments.
         *
         * If the expression is a variable, we try to resolve it and to check if it is initialized
         * with a constant `listOf` etc. invocation: we want not to suggest unnecessary conversions
         * if they are simple to detect.
         *
         * @param element the element to check
         */
        private fun isLikeConstantExpressionListOf(element: KtExpression): Boolean {
            return when (element) {
                is KtNameReferenceExpression -> {
                    // Try to resolve the name and check if it is declared with a constant `listOf`-like function
                    val candidate = resolveName(element)
                    if (candidate is KtVariableDeclaration && !candidate.isVar)
                        candidate.children.mapNotNull { getCallExpressionIfAny(it) }.run {
                            isNotEmpty() && all { it.isLikeConstantExpressionListOf() }
                        }
                    else false
                }
                else -> getCallExpressionIfAny(element)?.isLikeConstantExpressionListOf() ?: false
            }
        }

        // Extract the call expression from a possibly qualified function call.
        // Returns null if the call expression can't be found.
        private fun getCallExpressionIfAny(element: PsiElement?): KtCallExpression? = when(element) {
            is KtCallExpression -> element
            is KtQualifiedExpression -> element.selectorExpression as? KtCallExpression
            else -> null
        }

        // Check if a call expression is an immediate call to `listOf`/`arrayOf`/`sequenceOf` etc.,
        // that it has a relatively small number of arguments,
        // and that all its arguments are constant expressions.
        private fun KtCallExpression.isLikeConstantExpressionListOf(): Boolean {
            val callee = calleeExpression.safeAs<KtNameReferenceExpression>() ?: return false
            val candidate = resolveName(callee)
            return candidate is KtNamedFunction
                    && candidate.fqName?.asString() in constructorFunctions
                    && valueArguments.size <= 10
                    && valueArguments.all { arg -> arg.getArgumentExpression()?.isConstant() == true }
        }

        /**
         * Resolve a Kotlin name reference expression.
         *
         * @param element an expression to resolve
         * @return the PSI element corresponding to the declaration of the name or null if resolution fails
         */
        private fun resolveName(element: KtElement): PsiElement? {
            val editor = element.findExistingEditor() ?: return null
            return TargetElementUtil.findReference(editor, element.textOffset)?.resolve()
        }

        /**
         * Utility function to check if a type or any of its supertypes matches the condition.
         *
         * @param predicate the required property of the type
         */
        private fun KotlinType.hasMatchingSupertype(predicate: (KotlinType) -> Boolean): Boolean {
            return predicate(this) || constructor.supertypes.any { it.hasMatchingSupertype(predicate) }
        }
    }
}

/**
 * A wrapper class for potentially affected function names.
 *
 * It contains the function name (e.g., "minus") and optionally the fully qualified resolved name
 * (e.g., "kotlin.collections.minus"). Resolved name is null if no resolving has been performed,
 * or if the resolving attempt failed (e.g., `HashSet` class is a type alias of the Java class
 * on the JVM target platform, so its `removeAll` method can't be resolved).
 *
 * @param name function name from the call expression
 * @param resolvedName the fully qualified resolved name or null
 * @property isResolved the boolean flag that specifies if the resolved name is available
 */
private data class CandidateFunction(val name: String, val resolvedName: String?) {
    val isResolved: Boolean = resolvedName != null
}

// The set of `listOf`-like constructor functions
private val constructorFunctions = setOf(
    "kotlin.arrayOf",
    "kotlin.emptyArray",
    "kotlin.sequences.sequenceOf",
    "kotlin.sequences.emptySequence",
    "kotlin.collections.listOf",
    "kotlin.collections.emptyList"
)

// The set of fully qualified names of standard library functions to which the intention applies.
// We need this check to avoid false positives for user-defined functions that shadow
// stdlib functions of the same name.
private val relevantStdlibFunctions = setOf(
    "kotlin.collections.minus",
    "kotlin.sequences.minus",
    "kotlin.collections.minusAssign",
    "kotlin.collections.intersect",
    "kotlin.collections.subtract",
    "kotlin.collections.removeAll",
    "kotlin.collections.MutableCollection.removeAll",
    "kotlin.collections.MutableSet.removeAll",
    "kotlin.collections.retainAll",
    "kotlin.collections.MutableCollection.retainAll",
    "kotlin.collections.MutableSet.retainAll"
)

// The argument types we want to convert:
// - Array<T>
// - ArrayList<T>
// - Other types are super-types of ArrayList, so if we have an exact match here, we should suggest conversion
//   as the argument may be ArrayList (in this case we can suggest conversion for more types than necessary,
//   but we are OK with it)
private val alwaysConvertibleExactTypes = setOf(
    "kotlin.Array",
    "kotlin.collections.Iterable",
    "kotlin.collections.MutableIterable",
    "kotlin.collections.Collection",
    "kotlin.collections.MutableCollection",
    "kotlin.collections.List",
    "kotlin.collections.MutableList",
    "kotlin.collections.AbstractMutableList",
    "kotlin.collections.ArrayList",
    "java.util.ArrayList"
)

// The map of affected functions and their compatible receivers.
private val compatibleReceiverTypes = mapOf(
    "minus" to setOf(
        "kotlin.collections.Set",
        "kotlin.collections.Iterable",
        "kotlin.collections.Map",
        "kotlin.sequences.Sequence"
    ),

    "minusAssign" to setOf(
        "kotlin.collections.MutableCollection",
        "kotlin.collections.MutableMap"
    ),

    "intersect" to setOf(
        "kotlin.Array",
        "kotlin.ByteArray",
        "kotlin.ShortArray",
        "kotlin.IntArray",
        "kotlin.LongArray",
        "kotlin.FloatArray",
        "kotlin.DoubleArray",
        "kotlin.BooleanArray",
        "kotlin.CharArray",
        "kotlin.collections.Iterable"
    ),

    "subtract" to setOf(
        "kotlin.Array",
        "kotlin.ByteArray",
        "kotlin.ShortArray",
        "kotlin.IntArray",
        "kotlin.LongArray",
        "kotlin.FloatArray",
        "kotlin.DoubleArray",
        "kotlin.BooleanArray",
        "kotlin.CharArray",
        "kotlin.collections.Iterable"
    ),

    "removeAll" to setOf(
        "kotlin.collections.MutableCollection"
    ),

    "retainAll" to setOf(
        "kotlin.collections.MutableCollection"
    ),
)

