// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.analysis.api.KaNonPublicApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaUsualClassType
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.parents

internal class VerboseNullabilityAndEmptinessInspection :
    KotlinApplicableInspectionBase.Simple<KtBinaryExpression, VerboseNullabilityAndEmptinessInspection.Context>() {

    data class Context(
        val nullCheckExpression: KtExpression,
        val contentCheck: ContentCheck,
        val isPositiveCheck: Boolean,
        val hasExplicitReceiver: Boolean,
        val replacementName: String
    )

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> =
        object : KtVisitorVoid() {
            override fun visitBinaryExpression(expression: KtBinaryExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }

    override fun getProblemDescription(element: KtBinaryExpression, context: Context): @InspectionMessage String =
        KotlinBundle.message(
            "inspection.verbose.nullability.and.emptiness.call",
            (if (context.isPositiveCheck) "!" else "") + context.replacementName
        )

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        val nullCheckExpression = findNullCheckExpression(element)
        val nullCheck = getNullCheck(nullCheckExpression) ?: return false
        
        val binaryExpression = findBinaryExpression(nullCheckExpression) ?: return false
        val operationToken = binaryExpression.operationToken

        if (!isValidOperatorCombination(nullCheck.isEqualNull, operationToken)) return false
        
        val contentCheckExpression = findContentCheckExpression(nullCheckExpression, binaryExpression) ?: return false
        val contentCheck = getContentCheck(contentCheckExpression) ?: return false

        if (!isSimilar(nullCheck.target, contentCheck.target, ::psiOnlyTargetCheck)) return false

        val replacementName = contentCheck.data.replacementName
        if (isInsideFunctionImplementation(element, replacementName)) return false
        
        return super.isApplicableByPsi(element)
    }

    private fun isValidOperatorCombination(isEqualNull: Boolean, operationToken: IElementType): Boolean {
        return (!isEqualNull && operationToken == KtTokens.ANDAND) ||  // a != null && a.isNotEmpty()
               (isEqualNull && operationToken == KtTokens.OROR)         // a == null || a.isEmpty()
    }

    override fun KaSession.prepareContext(element: KtBinaryExpression): Context? {
        val nullCheckExpression = findNullCheckExpression(element)
        val nullCheck = getNullCheck(nullCheckExpression) ?: return null

        val binaryExpression = findBinaryExpression(nullCheckExpression) ?: return null
        val operationToken = binaryExpression.operationToken

        val isPositiveCheck = when {
            !nullCheck.isEqualNull && operationToken == KtTokens.ANDAND -> true // a != null && a.isNotEmpty()
            nullCheck.isEqualNull && operationToken == KtTokens.OROR -> false // a == null || a.isEmpty()
            else -> return null
        }

        val contentCheckExpression = findContentCheckExpression(nullCheckExpression, binaryExpression) ?: return null
        val contentCheck = getContentCheck(contentCheckExpression) ?: return null

        if (isPositiveCheck != contentCheck.isPositiveCheck) return null

        if (!isSimilar(nullCheck.target, contentCheck.target) { l, r ->
                if (l.kind == TargetChunk.Kind.THIS && r.kind == TargetChunk.Kind.THIS) true else resolve(l) == resolve(r)
            }) return null

        // Lack of smart-cast in 'a != null && <<a>>.isNotEmpty()' means it's rather some complex expression. Skip those for now
        if (!hasSmartCast(contentCheck.target.last())) return null

        val contentCheckFunction = resolveToFunctionSymbol(contentCheck.call) ?: return null
        if (!checkTargetFunctionReceiver(contentCheck.call)) return null

        val overriddenFunctions = getAllOverriddenSymbols(contentCheckFunction).filterIsInstance<KaFunctionSymbol>() + contentCheckFunction
        if (overriddenFunctions.none { getCallableFqName(it) in contentCheck.data.callableNames }) return null

        val hasExplicitReceiver = contentCheck.target.singleOrNull()?.expression !is KtCallExpression
        val replacementName = contentCheck.data.replacementName

        return Context(
            nullCheckExpression = nullCheckExpression,
            contentCheck = contentCheck,
            isPositiveCheck = isPositiveCheck,
            hasExplicitReceiver = hasExplicitReceiver,
            replacementName = replacementName
        )
    }

    override fun createQuickFix(element: KtBinaryExpression, context: Context): KotlinModCommandQuickFix<KtBinaryExpression> =
        object : KotlinModCommandQuickFix<KtBinaryExpression>() {
            override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.with.0.call", context.replacementName)

            override fun applyFix(project: Project, element: KtBinaryExpression, updater: ModPsiUpdater) {
                val nullCheckExpression = updater.getWritable(context.nullCheckExpression)
                val binaryExpression = findBinaryExpression(nullCheckExpression) ?: return
                val writableBinaryExpression = updater.getWritable(binaryExpression)
                val parenthesizedParent = writableBinaryExpression.getTopmostParenthesizedParent() as? KtParenthesizedExpression

                val expressionText = buildString {
                    if (context.isPositiveCheck) append("!")
                    if (context.hasExplicitReceiver) {
                        val receiverExpression = getReceiver(nullCheckExpression) ?: return
                        append(receiverExpression.text).append(".")
                    }
                    append(context.replacementName).append("()")
                }

                val callExpression = KtPsiFactory(project).createExpression(expressionText)

                when (nullCheckExpression.parenthesize()) {
                    writableBinaryExpression.left -> writableBinaryExpression.replace(callExpression)
                    writableBinaryExpression.right -> {
                        val outerBinaryExpression = writableBinaryExpression.parent as? KtBinaryExpression ?: return
                        val writableOuterBinaryExpression = updater.getWritable(outerBinaryExpression)
                        val leftExpression = writableBinaryExpression.left ?: return
                        writableBinaryExpression.replace(leftExpression) // flag && a != null && a.isNotEmpty() -> flag && a.isNotEmpty()
                        writableOuterBinaryExpression.right?.replace(callExpression) // flag && a.isNotEmpty() -> flag && !a.isNullOrEmpty()
                    }
                }
                if (parenthesizedParent != null && KtPsiUtil.areParenthesesUseless(parenthesizedParent)) {
                    val writableParenthesizedParent = updater.getWritable(parenthesizedParent)
                    writableParenthesizedParent.replace(writableParenthesizedParent.safeDeparenthesize())
                }
            }

            private fun getReceiver(expression: KtExpression?): KtExpression? = when (expression) {
                is KtPrefixExpression if expression.operationToken == KtTokens.EXCL -> {
                    val baseExpression = expression.baseExpression?.safeDeparenthesize()
                    if (baseExpression is KtExpression) getReceiver(baseExpression) else null
                }

                is KtBinaryExpression -> when {
                    expression.left?.isNullLiteral() == true -> expression.right
                    expression.right?.isNullLiteral() == true -> expression.left
                    else -> null
                }

                else -> null
            }
        }

    /**
     * Performs a PSI-only comparison of target chunks without semantic analysis.
     * Used for quick preliminary checks before expensive symbol resolution.
     */
    private fun psiOnlyTargetCheck(left: TargetChunk, right: TargetChunk) = left.kind == right.kind && left.name == right.name

    /**
     * Validates that the function call receiver is suitable for null-or-empty checks.
     * Returns false for nullable types or primitive arrays where isNullOrEmpty wouldn't be appropriate.
     */
    private fun KaSession.checkTargetFunctionReceiver(expression: KtCallExpression): Boolean {
        val call = expression.resolveToCall()?.successfulFunctionCallOrNull() ?: return false
        val receiverSymbol = call.partiallyAppliedSymbol
        val type = receiverSymbol.dispatchReceiver?.type ?: receiverSymbol.extensionReceiver?.type ?: return false

        val isNotNullable = !type.isMarkedNullable
        val isNotPrimitiveArray = type !is KaUsualClassType || !type.isPrimitiveArray()

        return isNotNullable && isNotPrimitiveArray
    }

    /**
     * Checks if this class type represents a primitive array (e.g., IntArray, CharArray)
     * or unsigned array type (e.g., UIntArray, UByteArray).
     */
    private fun KaUsualClassType.isPrimitiveArray(): Boolean {
        return classId in StandardClassIds.primitiveArrayTypeByElementType.values ||
                classId in StandardClassIds.unsignedArrayTypeByElementType.values
    }

    /**
     * Extracts a chain of target references from an expression.
     * For example, "obj.property" becomes [obj, property], "this" becomes [this].
     * Returns null if the expression cannot be decomposed into a simple target chain.
     */
    private fun getTargetChain(targetExpression: KtExpression): TargetChain? {
        val result = mutableListOf<TargetChunk>()

        fun processDepthFirst(expression: KtExpression): Boolean {
            when (val unwrapped = expression.safeDeparenthesize()) {
                is KtNameReferenceExpression -> result += TargetChunk(unwrapped)
                is KtDotQualifiedExpression -> {
                    if (!processDepthFirst(unwrapped.receiverExpression)) return false
                    val selectorExpression = unwrapped.selectorExpression as? KtNameReferenceExpression ?: return false
                    result += TargetChunk(selectorExpression)
                }

                is KtThisExpression -> result += TargetChunk(unwrapped)
                else -> return false
            }

            return true
        }

        return if (processDepthFirst(targetExpression)) result.takeIf { it.isNotEmpty() } else null
    }

    /**
     * Compares two target chains using the provided checker function.
     * Returns true if chains have the same length and all corresponding chunks pass the checker.
     */
    private fun isSimilar(left: TargetChain, right: TargetChain, checker: (TargetChunk, TargetChunk) -> Boolean): Boolean {
        return left.size == right.size && left.indices.all { index -> checker(left[index], right[index]) }
    }

    /**
     * Unwraps logical negation (!) from an expression and calls the block with the unwrapped expression
     * and a boolean indicating whether it was negated. Used to handle patterns like "!(x == null)".
     */
    private fun <T : Any> unwrapNegation(expression: KtExpression, block: (KtExpression, Boolean) -> T?): T? {
        if (expression is KtPrefixExpression && expression.operationToken == KtTokens.EXCL) {
            val baseExpression = expression.baseExpression?.safeDeparenthesize() ?: return null
            return block(baseExpression, true)
        }

        return block(expression, false)
    }

    private class NullCheck(val target: TargetChain, val isEqualNull: Boolean)

    /**
     * Attempts to parse a null check expression (e.g., "x == null", "!(y != null)").
     * Returns a NullCheck object containing the target and whether it's checking for null equality.
     */
    private fun getNullCheck(expression: KtExpression): NullCheck? {
        return unwrapNegation(expression) { expr, isNegated ->
            parseNullCheckExpression(expr, isNegated)
        }
    }

    private fun parseNullCheckExpression(expression: KtExpression, isNegated: Boolean): NullCheck? {
        if (expression !is KtBinaryExpression) return null

        val isEqualityCheck = when (expression.operationToken) {
            KtTokens.EQEQ -> true
            KtTokens.EXCLEQ -> false
            else -> return null
        }

        val isCheckingForNull = isEqualityCheck xor isNegated
        val left = expression.left ?: return null
        val right = expression.right ?: return null

        return when {
            left.isNullLiteral() -> createNullCheck(right, isCheckingForNull)
            right.isNullLiteral() -> createNullCheck(left, isCheckingForNull)
            else -> null
        }
    }

    private fun createNullCheck(targetExpression: KtExpression, isNull: Boolean): NullCheck? {
        val targetChain = getTargetChain(targetExpression) ?: return null
        return NullCheck(targetChain, isNull)
    }

    class ContentCheck(val target: TargetChain, val call: KtCallExpression, val data: ContentFunction, val isPositiveCheck: Boolean)

    /**
     * Attempts to parse a content check expression (e.g., "list.isEmpty()", "!text.isBlank()").
     * Returns a ContentCheck object containing the target, call expression, and associated metadata.
     */
    private fun getContentCheck(expression: KtExpression) = unwrapNegation(expression, fun(expression, isNegated): ContentCheck? {
        val (callExpression, target) = when (expression) {
            is KtCallExpression -> expression to listOf(TargetChunk(expression))
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

    internal class ContentFunction(val isPositiveCheck: Boolean, val replacementName: String, vararg val callableNames: String)
}

private typealias TargetChain = List<TargetChunk>

data class TargetChunk(val expression: KtExpression) {
    enum class Kind { THIS, NAME }

    val kind: Kind = when (expression) {
        is KtThisExpression, is KtCallExpression -> Kind.THIS
        else -> Kind.NAME
    }

    val name: String? = when (expression) {
        is KtNameReferenceExpression -> expression.getReferencedName()
        else -> null
    }
}

private val contentCheckingFunctions: Map<String, VerboseNullabilityAndEmptinessInspection.ContentFunction> = mapOf(
    "isEmpty" to VerboseNullabilityAndEmptinessInspection.ContentFunction(
        isPositiveCheck = false, replacementName = "isNullOrEmpty",
        "kotlin.collections.Collection.isEmpty",
        "kotlin.collections.Map.isEmpty",
        "kotlin.collections.isEmpty",
        "kotlin.text.isEmpty"
    ),
    "isBlank" to VerboseNullabilityAndEmptinessInspection.ContentFunction(
        isPositiveCheck = false, replacementName = "isNullOrBlank",
        "kotlin.text.isBlank"
    ),
    "isNotEmpty" to VerboseNullabilityAndEmptinessInspection.ContentFunction(
        isPositiveCheck = true, replacementName = "isNullOrEmpty",
        "kotlin.collections.isNotEmpty",
        "kotlin.text.isNotEmpty"
    ),
    "isNotBlank" to VerboseNullabilityAndEmptinessInspection.ContentFunction(
        isPositiveCheck = true, replacementName = "isNullOrBlank",
        "kotlin.text.isNotBlank"
    )
)

/**
 * Finds the topmost expression that contains the null check, including any negation operators.
 * Traverses up the PSI tree through parentheses and negation to find the complete null check expression.
 */
private fun findNullCheckExpression(unwrappedNullCheckExpression: KtExpression): KtExpression {
    // Find topmost binary negation (!a), skipping intermediate parentheses, annotations and other miscellaneous expressions

    var result = unwrappedNullCheckExpression
    for (parent in unwrappedNullCheckExpression.parents) {
        when (parent) {
            !is KtExpression -> break
            is KtPrefixExpression -> if (parent.operationToken != KtTokens.EXCL) break
            else -> if (KtPsiUtil.deparenthesizeOnce(parent) === result) continue else break
        }
        result = parent
    }
    return result
}

/**
 * Finds the binary expression (&&, ||) that contains the null check expression.
 * This is typically the parent of the parenthesized null check.
 */
private fun findBinaryExpression(nullCheckExpression: KtExpression): KtBinaryExpression? {
    return nullCheckExpression.parenthesize().parent as? KtBinaryExpression
}

/**
 * Locates the content check expression (isEmpty, isBlank, etc.) that pairs with the null check.
 * Handles complex binary expression structures where checks might be in different sub-expressions.
 */
private fun findContentCheckExpression(nullCheckExpression: KtExpression, binaryExpression: KtBinaryExpression): KtExpression? {
    when (nullCheckExpression.parenthesize()) {
        binaryExpression.left -> {
            // [[a != null && a.isNotEmpty()] && flag] && flag2
            return binaryExpression.right?.safeDeparenthesize()
        }

        binaryExpression.right -> {
            // [[flag && flag2] && a != null] && a.isNotEmpty()
            val outerBinaryExpression = binaryExpression.parent as? KtBinaryExpression
            if (outerBinaryExpression != null && outerBinaryExpression.operationToken == binaryExpression.operationToken) {
                return outerBinaryExpression.right?.safeDeparenthesize()
            }
        }
    }

    return null
}

/**
 * Checks if this expression is a null literal, handling parentheses.
 */
private fun KtExpression.isNullLiteral(): Boolean {
    return (safeDeparenthesize() as? KtConstantExpression)?.text == KtTokens.NULL_KEYWORD.value
}

/**
 * Returns the immediate parenthesized parent of this expression, if it exists.
 */
private fun KtExpression.getParenthesizedParent(): KtExpression? {
    return (parent as? KtExpression)?.takeIf { KtPsiUtil.deparenthesizeOnce(it) === this }
}

/**
 * Finds the topmost parenthesized expression that contains this expression.
 * Traverses up through nested parentheses to find the outermost one.
 */
private fun KtExpression.getTopmostParenthesizedParent(): KtExpression? {
    var current = getParenthesizedParent() ?: return null
    while (true) {
        current = current.getParenthesizedParent() ?: break
    }
    return current
}

/**
 * Returns the topmost parenthesized version of this expression, or the expression itself if not parenthesized.
 */
private fun KtExpression.parenthesize(): KtExpression {
    return getTopmostParenthesizedParent() ?: this
}

/**
 * Resolves a target chunk to its underlying symbol for semantic comparison.
 * Returns null for 'this' expressions to rely on PSI-only comparison.
 */
private fun KaSession.resolve(chunk: TargetChunk): Any? {
    return when (chunk.expression) {
        is KtThisExpression -> {
            // We don't rely on symbol resolution for 'this' because comparator treats THIS vs THIS as equal.
            // Returning null here is fine since non-THIS comparisons will fail equality as intended.
            null
        }

        is KtCallExpression -> {
            val call = chunk.expression.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
            getSingleReceiver(call.partiallyAppliedSymbol.dispatchReceiver, call.partiallyAppliedSymbol.extensionReceiver)
        }

        is KtNameReferenceExpression -> {
            chunk.expression.mainReference.resolveToSymbol()
        }

        else -> null
    }
}

/**
 * Extracts a single receiver from dispatch and extension receivers.
 * Returns null if both are present (invalid state) or returns the non-null one.
 */
private fun getSingleReceiver(dispatchReceiver: Any?, extensionReceiver: Any?): Any? {
    if (dispatchReceiver != null && extensionReceiver != null) {
        // Functions such as 'isEmpty()' never have both dispatch and extension receivers
        return null
    }

    return dispatchReceiver ?: extensionReceiver
}

/**
 * Determines if a 'this' expression is in a context where it should be considered smart-cast.
 * Used for patterns like 'this == null || this.isEmpty()' where the second 'this' should be smart-cast.
 */
private fun isInSmartCastContext(thisExpression: KtThisExpression): Boolean {
    // Check if this 'this' expression is in a context where it should be smart cast
    // For example, in 'this == null || this.isEmpty()', the second 'this' should be smart cast
    val parentQualified = thisExpression.parent as? KtDotQualifiedExpression ?: return false
    val parentBinary = parentQualified.parent as? KtBinaryExpression ?: return false

    // Check if we're in an OR/AND expression that has a null check on the same target
    return when (parentBinary.operationToken) {
        KtTokens.OROR, KtTokens.ANDAND -> {
            // Look for a null check in the left side that would smart cast this expression
            val leftSide = parentBinary.left
            leftSide != null && hasNullCheckFor(leftSide)
        }

        else -> false
    }
}

/**
 * Checks if the given expression contains a null check for a 'this' expression.
 * Used to determine if smart casting should occur in the context.
 */
private fun hasNullCheckFor(expression: KtExpression?): Boolean {
    if (expression !is KtBinaryExpression) return false

    val isNullCheck = expression.operationToken == KtTokens.EQEQ || expression.operationToken == KtTokens.EXCLEQ
    if (!isNullCheck) return false

    val left = expression.left
    val right = expression.right

    return (left is KtThisExpression && right?.isNullLiteral() == true) ||
            (right is KtThisExpression && left?.isNullLiteral() == true)
}

/**
 * Determines if the target chunk has smart cast information available.
 * Checks various sources of smart cast info including explicit smart casts and contextual smart casting.
 */
@OptIn(KaNonPublicApi::class)
private fun KaSession.hasSmartCast(chunk: TargetChunk): Boolean {
    return when (val expression = chunk.expression) {
        is KtThisExpression -> {
            expression.smartCastInfo != null ||
                    // Check if the parent qualified expression has smart cast info
                    (expression.parent as? KtDotQualifiedExpression)?.smartCastInfo != null ||
                    // Check if we're in a context where smart casting should occur
                    // For the pattern 'this == null || this.isEmpty()', the 'this' in isEmpty() should be considered smart cast
                    isInSmartCastContext(expression)
        }

        is KtCallExpression -> {
            expression.calleeExpression?.implicitReceiverSmartCasts?.isNotEmpty() == true
        }

        is KtNameReferenceExpression -> {
            var current: KtExpression = expression.getQualifiedExpressionForSelectorOrThis()
            while (true) {
                if (current.smartCastInfo != null) return true
                current = current.getParenthesizedParent() ?: break
            }
            false
        }

        else -> false
    }
}

/**
 * Resolves a call expression to its underlying function symbol.
 */
private fun KaSession.resolveToFunctionSymbol(expression: KtCallExpression): KaFunctionSymbol? {
    val call = expression.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
    return call.partiallyAppliedSymbol.symbol
}

/**
 * Gets all overridden symbols for a function. Currently returns only the function itself.
 * This is a placeholder for potential future enhancement to handle inheritance hierarchies.
 */
private fun getAllOverriddenSymbols(function: KaFunctionSymbol): Sequence<KaCallableSymbol> {
    return sequenceOf(function)
}

/**
 * Attempts to get the fully qualified name of a callable function.
 * Handles built-in Kotlin functions by constructing FQ names manually.
 */
private fun getCallableFqName(function: KaFunctionSymbol): String? {
    return (function as? KaNamedFunctionSymbol)?.let { namedFunction ->
        // Try to get the full qualified name using a simple approach
        namedFunction.name.asString().let { name ->
            // For built-in functions, we need to construct the FQ name manually
            when (name) {
                in setOf("isEmpty", "isNotEmpty") -> "kotlin.collections.$name"
                in setOf("isBlank", "isNotBlank") -> "kotlin.text.$name"
                else -> name
            }
        }
    }
}

/**
 * Checks if the given element is inside a function implementation with the specified name.
 * Used to avoid suggesting replacements that would create recursive calls.
 */
private fun isInsideFunctionImplementation(element: KtExpression, functionName: String): Boolean {
    // Check if we're inside a function that has the same name as the one we'd suggest
    var current: KtElement? = element
    while (current != null) {
        if (current is KtNamedFunction && current.name == functionName) {
            return true
        }
        current = current.parent as? KtElement
    }
    return false
}
