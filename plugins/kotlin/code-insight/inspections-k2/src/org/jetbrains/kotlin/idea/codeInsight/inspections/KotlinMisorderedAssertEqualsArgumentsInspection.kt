// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.components.evaluate
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaJavaFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaLocalVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.realName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

private val ASSERT_METHOD_NAMES = setOf(
    "assertEquals",
    "assertEqualsNoOrder",
    "assertNotEquals",
    "assertArrayEquals",
    "assertSame",
    "assertNotSame",
    "failNotSame",
    "failNotEquals",
)

private val ASSERT_FUNCTION_OWNERS = setOf(
    "junit.framework.Assert",
    "junit.framework.TestCase",
    "org.junit.Assert",
    "org.junit.jupiter.api.Assertions",
    "org.testng.Assert",
    "org.testng.AssertJUnit",
    "kotlin.test",
)

private val EXPECTED_LIKE_FACTORY_CALLS = setOf(
    "kotlin.arrayOf",
    "kotlin.booleanArrayOf",
    "kotlin.byteArrayOf",
    "kotlin.charArrayOf",
    "kotlin.collections.listOf",
    "kotlin.collections.mapOf",
    "kotlin.collections.setOf",
    "kotlin.doubleArrayOf",
    "kotlin.emptyArray",
    "kotlin.floatArrayOf",
    "kotlin.intArrayOf",
    "kotlin.longArrayOf",
    "kotlin.shortArrayOf",
)

internal class KotlinMisorderedAssertEqualsArgumentsInspection :
    KotlinApplicableInspectionBase.Simple<KtCallExpression, KotlinMisorderedAssertEqualsArgumentsInspection.Context>() {

    data class Context(
        val methodName: String,
        val expectedArgumentIndex: Int,
        val actualArgumentIndex: Int,
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitorVoid = object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        val methodName = element.calleeExpression?.text ?: return false
        if (methodName !in ASSERT_METHOD_NAMES) return false
        val arguments = element.valueArguments
        return arguments.size >= 2 && arguments.none { it.isNamed() }
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val call = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val functionSymbol = call.symbol
        val methodName = functionSymbol.assertMethodNameOrNull() ?: return null

        val assertArguments = element.findAssertArguments(call) ?: return null
        if (assertArguments.expected.expression.looksLikeExpectedArgument(ParameterPosition.EXPECTED)) return null
        if (!assertArguments.actual.expression.looksLikeExpectedArgument(ParameterPosition.ACTUAL)) return null

        return Context(
            methodName = methodName,
            expectedArgumentIndex = assertArguments.expected.index,
            actualArgumentIndex = assertArguments.actual.index,
        )
    }

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> =
        ApplicabilityRange.single(element) { it.calleeExpression ?: it }

    override fun getProblemDescription(
        element: KtCallExpression,
        context: Context,
    ): @InspectionMessage String =
        KotlinBundle.message("misordered.assert.equals.arguments.problem.descriptor", context.methodName)

    override fun createQuickFix(
        element: KtCallExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtCallExpression> = FlipComparedArgumentsFix(context.expectedArgumentIndex, context.actualArgumentIndex)

    private class FlipComparedArgumentsFix(
        private val expectedArgumentIndex: Int,
        private val actualArgumentIndex: Int,
    ) : KotlinModCommandQuickFix<KtCallExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("misordered.assert.equals.arguments.flip.quickfix")

        override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
            val arguments = element.valueArguments
            val expectedArgument =
                arguments.getOrNull(expectedArgumentIndex)?.takeUnless { it.isNamed() } ?: return
            val actualArgument =
                arguments.getOrNull(actualArgumentIndex)?.takeUnless { it.isNamed() } ?: return

            val writableExpectedArgument = updater.getWritable(expectedArgument)
            val writableActualArgument = updater.getWritable(actualArgument)
            val expectedCopy = writableExpectedArgument.copy()
            val actualCopy = writableActualArgument.copy()

            writableExpectedArgument.replace(actualCopy)
            writableActualArgument.replace(expectedCopy)
        }
    }

    private data class Argument(val index: Int, val expression: KtExpression)

    private data class AssertArguments(val expected: Argument, val actual: Argument)

    context(_: KaSession)
    private fun KtCallExpression.findArgumentWithName(call: KaFunctionCall<*>, name: String) =
        valueArguments.withIndex().firstNotNullOfOrNull { (index, argument) ->
            val argumentExpression = argument.getArgumentExpression() ?: return@firstNotNullOfOrNull null
            val symbol = call.valueArgumentMapping[argumentExpression]?.symbol
            val parameterName = symbol?.stableName ?: return@firstNotNullOfOrNull null
            if (parameterName.asString() == name) Argument(index, argumentExpression) else null
        }

    context(_: KaSession)
    private fun KtCallExpression.findAssertArguments(call: KaFunctionCall<*>): AssertArguments? {
        val expectedArgument = findArgumentWithName(call, "expected") ?: return null
        val actualArgument = findArgumentWithName(call, "actual") ?: return null

        return AssertArguments(expectedArgument, actualArgument)
    }

    private fun KaFunctionSymbol.assertMethodNameOrNull(): String? {
        val callableId = callableId ?: return null
        val methodName = callableId.callableName.asString()
        if (methodName !in ASSERT_METHOD_NAMES) return null
        val ownerName = callableId.asSingleFqName().asString().removeSuffix(".$methodName")
        return methodName.takeIf { ownerName in ASSERT_FUNCTION_OWNERS }
    }

    context(_: KaSession)
    private val KaValueParameterSymbol.stableName: Name?
        get() = (realName ?: name).takeUnless(Name::isSpecial)

    context(_: KaSession)
    private fun KtExpression.looksLikeExpectedArgument(
        parameterPosition: ParameterPosition,
        visited: MutableSet<KtExpression> = mutableSetOf(),
    ): Boolean {
        val expression = unwrapParentheses()
        if (!visited.add(expression)) return false

        val constant = expression.evaluate()
        return when {
            constant != null && constant !is KaConstantValue.ErrorValue -> true
            expression is KtConstantExpression -> true
            expression is KtStringTemplateExpression -> !expression.hasInterpolation()
            expression is KtNameReferenceExpression -> expression.looksLikeExpectedReference(parameterPosition, visited)
            expression is KtDotQualifiedExpression -> expression.looksLikeExpectedQualifiedExpression(parameterPosition, visited)
            expression is KtCallExpression -> expression.looksLikeExpectedCall(receiverExpression = null, parameterPosition, visited)
            else -> false
        }
    }

    context(_: KaSession)
    private fun KtDotQualifiedExpression.looksLikeExpectedQualifiedExpression(
        parameterPosition: ParameterPosition,
        visited: MutableSet<KtExpression>,
    ): Boolean {
        return when (val selector = selectorExpression) {
            is KtNameReferenceExpression -> selector.looksLikeExpectedReference(parameterPosition, visited)
            is KtCallExpression -> selector.looksLikeExpectedCall(receiverExpression, parameterPosition, visited)
            else -> selector?.looksLikeExpectedArgument(parameterPosition, visited) == true
        }
    }

    context(_: KaSession)
    private fun KtCallExpression.looksLikeExpectedCall(
        receiverExpression: KtExpression?,
        parameterPosition: ParameterPosition,
        visited: MutableSet<KtExpression>,
    ): Boolean {
        val functionSymbol = resolveToCall()?.successfulFunctionCallOrNull()?.symbol as? KaNamedFunctionSymbol ?: return false
        if (parameterPosition == ParameterPosition.ACTUAL && functionSymbol.name.asString() == "expected") return true

        val allArgumentsAreExpectedLike = valueArguments.all { argument ->
            argument.getArgumentExpression()?.looksLikeExpectedArgument(parameterPosition, visited) == true
        }
        if (!allArgumentsAreExpectedLike) return false

        val callableName = functionSymbol.callableId?.asSingleFqName()?.asString()
        return callableName in EXPECTED_LIKE_FACTORY_CALLS || receiverExpression?.isClassLikeQualifier() == true
    }

    context(_: KaSession)
    private fun KtNameReferenceExpression.looksLikeExpectedReference(
        parameterPosition: ParameterPosition,
        visited: MutableSet<KtExpression>,
    ): Boolean = mainReference.resolveToSymbol()?.looksLikeExpectedSymbol(parameterPosition, visited) == true

    context(_: KaSession)
    private fun KaSymbol.looksLikeExpectedSymbol(
        parameterPosition: ParameterPosition,
        visited: MutableSet<KtExpression>,
    ): Boolean {
        return when (this) {
            is KaEnumEntrySymbol, is KaClassSymbol -> true
            is KaNamedFunctionSymbol -> parameterPosition == ParameterPosition.ACTUAL && name.asString() == "expected"
            is KaValueParameterSymbol -> name.asString() == "expected"
            is KaLocalVariableSymbol -> {
                if (name.asString() == "expected") return true
                val property = psi as? KtProperty ?: return false
                !property.isVar && property.initializer?.looksLikeExpectedArgument(parameterPosition, visited) == true
            }
            is KaPropertySymbol -> {
                if (name.asString() == "expected") return true
                val property = psi as? KtProperty
                property?.hasModifier(KtTokens.CONST_KEYWORD) == true && property.initializer?.looksLikeExpectedArgument(parameterPosition, visited) == true
            }
            is KaJavaFieldSymbol -> {
                val field = psi as? PsiField ?: return false
                field is PsiEnumConstant || field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)
            }
            else -> false
        }
    }

    context(_: KaSession)
    private fun KtExpression.isClassLikeQualifier(): Boolean {
        return when (val expression = unwrapParentheses()) {
            is KtNameReferenceExpression -> expression.mainReference.resolveToSymbol() is KaClassSymbol
            is KtDotQualifiedExpression -> expression.selectorExpression?.isClassLikeQualifier() == true
            else -> false
        }
    }

    private fun KtExpression.unwrapParentheses(): KtExpression {
        var expression = this
        while (expression is KtParenthesizedExpression) {
            expression = expression.expression ?: break
        }
        return expression
    }

    private enum class ParameterPosition {
        EXPECTED,
        ACTUAL,
    }
}
