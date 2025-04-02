// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * Detects function calls where an argument could be converted to `Set` to improve performance.
 * The quick-fix appends `toSet()` conversion to the argument.
 */
class ConvertArgumentToSetInspection : KotlinApplicableInspectionBase<KtExpression, List<KtExpression>>() {

    override fun isApplicableByPsi(element: KtExpression): Boolean = when (element) {
        is KtCallExpression -> true
        is KtBinaryExpression -> true
        else -> false
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }

        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtExpression, context: List<KtExpression>, rangeInElement: TextRange?, onTheFly: Boolean
    ): ProblemDescriptor {
        return createProblemDescriptor(
            /* psiElement = */ element,
            /* rangeInElement = */ context.firstOrNull()?.textRange?.shiftLeft(element.startOffset) ?: rangeInElement,
            /* descriptionTemplate = */ KotlinBundle.message("can.convert.argument.to.set"),
            /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            /* onTheFly = */ onTheFly,
            /* ...fixes = */ ConvertArgumentToSetFix(),
        )
    }

    private class ConvertArgumentToSetFix : KotlinModCommandQuickFix<KtExpression>() {
        override fun getFamilyName(): String = KotlinBundle.message("convert.argument.to.set.fix.text")

        override fun applyFix(project: Project, element: KtExpression, updater: ModPsiUpdater) {
            val arguments = analyze(element) {
                when (element) {
                    is KtCallExpression -> getConvertibleArguments(element)
                    is KtBinaryExpression -> getConvertibleArguments(element)
                    else -> null
                }
            } ?: return

            for (arg in arguments) {
                arg.replace(KtPsiFactory(project).createExpressionByPattern("$0.toSet()", arg))
            }
        }
    }

    override fun KaSession.prepareContext(element: KtExpression): List<KtExpression>? {
        return when (element) {
            is KtCallExpression -> getConvertibleArguments(element)
            is KtBinaryExpression -> getConvertibleArguments(element)
            else -> null
        }?.takeIf { it.isNotEmpty() }
    }
}

private fun KaSession.getConvertibleArguments(element: KtExpression): List<KtExpression> {
    return when (element) {
        is KtCallExpression -> getConvertibleCallArguments(element)
        is KtBinaryExpression -> getConvertibleBinaryArguments(element)
        else -> emptyList()
    }
}

private fun KaSession.getConvertibleCallArguments(element: KtCallExpression): List<KtExpression> {
    val receiverExpression = element.getQualifiedExpressionForSelector()?.receiverExpression ?: return emptyList()
    val receiverType = receiverExpression.expressionType ?: return emptyList()
    val calleeExpression = element.calleeExpression as? KtNameReferenceExpression ?: return emptyList()

    val candidateFunctionName = calleeExpression.getReferencedName()
    val candidateFunction = calleeExpression.mainReference.resolve() as? KtNamedFunction

    // Check that the callee name is not shadowed
    if (candidateFunction != null) {
        val fqName = candidateFunction.fqName?.asString()
        if (fqName == null || !relevantStdlibFunctions.contains(fqName)) return emptyList()
    }

    val compatibleReceivers = compatibleReceiverTypes[candidateFunctionName] ?: return emptyList()

    if (!compatibleReceivers.any { receiverType.isSubtypeOf(it) }) return emptyList()

    return element.valueArguments.mapNotNull {
        val arg = it.getArgumentExpression()
        if (arg != null && canConvertArgument(arg)) arg else null
    }
}

private fun KaSession.getConvertibleBinaryArguments(element: KtBinaryExpression): List<KtExpression> {
    val argument = element.right ?: return emptyList()
    val receiver = element.left ?: return emptyList()
    val receiverType = receiver.expressionType ?: return emptyList()

    val operationName = when (element.operationToken) {
        KtTokens.MINUS -> "minus"
        KtTokens.MINUSEQ -> "minusAssign"
        KtTokens.IDENTIFIER -> {
            val fqName = (element.operationReference.mainReference.resolve() as? KtNamedFunction)?.fqName
            fqName?.takeIf { it.asString() in relevantStdlibFunctions }?.shortName()?.asString()
        }

        else -> null
    } ?: return emptyList()

    val compatibleReceivers = compatibleReceiverTypes[operationName] ?: return emptyList()

    if (!compatibleReceivers.any { receiverType.isSubtypeOf(it) }) return emptyList()

    return if (canConvertArgument(argument)) listOf(argument) else emptyList()
}

private fun KaSession.canConvertArgument(argument: KtExpression): Boolean {
    val argumentType = argument.expressionType ?: return false

    // Functions that we are dealing with don't work with nullable types
    if (argumentType.isMarkedNullable) return false

    // Do not suggest the fix if the argument was constructed using `arrayOf`, `listOf`, `sequenceOf` etc
    if (isLikeConstantExpressionListOf(argument)) return false

    // Suggest the fix if the argument has exact type that we know is convertible
    if (alwaysConvertibleExactTypes.any { argumentType.symbol?.classId == it }) return true

    // Suggest the fix if the argument is a `Sequence`
    return argumentType.isSubtypeOf(sequenceClassId)
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
private fun KaSession.isLikeConstantExpressionListOf(element: KtExpression): Boolean {
    return when (element) {
        is KtNameReferenceExpression -> { // Try to resolve the name and check if it is declared with a constant `listOf`-like function
            val candidate = element.mainReference.resolve()
            if (candidate is KtVariableDeclaration && !candidate.isVar) candidate.children.mapNotNull { getCallExpressionIfAny(it) }.run {
                isNotEmpty() && all { it.isLikeConstantExpressionListOf() }
            }
            else false
        }

        else -> getCallExpressionIfAny(element)?.isLikeConstantExpressionListOf() ?: false
    }
}

// Extract the call expression from a possibly qualified function call.
// Returns null if the call expression can't be found.
private fun getCallExpressionIfAny(element: PsiElement?): KtCallExpression? = when (element) {
    is KtCallExpression -> element
    is KtQualifiedExpression -> element.selectorExpression as? KtCallExpression
    else -> null
}

// Check if a call expression is an immediate call to `listOf`/`arrayOf`/`sequenceOf` etc.,
// that it has a relatively small number of arguments, and that all its arguments are constant expressions.
context(KaSession) private fun KtCallExpression.isLikeConstantExpressionListOf(): Boolean {
    val callee = calleeExpression.safeAs<KtNameReferenceExpression>() ?: return false
    val candidate = callee.mainReference.resolve()
    return candidate is KtNamedFunction &&
            candidate.fqName?.asString() in constructorFunctions &&
            valueArguments.size <= 10 &&
            valueArguments.all { arg -> arg.getArgumentExpression()?.evaluate() != null }
}

// The set of fully qualified names of standard library functions to which the intention applies.
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

// The argument types we want to convert
private val alwaysConvertibleExactTypes = setOf(
    StandardClassIds.Array,
    StandardClassIds.Iterable,
    StandardClassIds.MutableIterable,
    StandardClassIds.Collection,
    StandardClassIds.MutableCollection,
    StandardClassIds.List,
    StandardClassIds.MutableList,
    ClassId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, Name.identifier("AbstractMutableList")),
    ClassId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, Name.identifier("ArrayList")),
    ClassId(FqName("java").child(Name.identifier("util")), Name.identifier("ArrayList"))
)

private val sequenceClassId = ClassId(StandardClassIds.BASE_SEQUENCES_PACKAGE, Name.identifier("Sequence"))

private val intersectSubtractCompatibleReceiverTypes = setOf(
    StandardClassIds.Array,
    ClassId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier("ByteArray")),
    ClassId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier("ShortArray")),
    ClassId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier("IntArray")),
    ClassId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier("LongArray")),
    ClassId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier("FloatArray")),
    ClassId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier("DoubleArray")),
    ClassId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier("BooleanArray")),
    ClassId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier("CharArray")),
    StandardClassIds.Iterable
)

// The map of affected functions and their compatible receivers.
private val compatibleReceiverTypes = mapOf(
    "minus" to setOf(
        StandardClassIds.Set, StandardClassIds.Iterable, StandardClassIds.Map, sequenceClassId
    ),

    "minusAssign" to setOf(
        StandardClassIds.MutableCollection, StandardClassIds.MutableMap
    ),

    "intersect" to intersectSubtractCompatibleReceiverTypes,

    "subtract" to intersectSubtractCompatibleReceiverTypes,

    "removeAll" to setOf(
        StandardClassIds.MutableCollection
    ),

    "retainAll" to setOf(
        StandardClassIds.MutableCollection
    ),
)

// The set of `listOf`-like constructor functions
private val constructorFunctions = setOf(
    "kotlin.arrayOf",
    "kotlin.emptyArray",
    "kotlin.sequences.sequenceOf",
    "kotlin.sequences.emptySequence",
    "kotlin.collections.listOf",
    "kotlin.collections.emptyList"
)
