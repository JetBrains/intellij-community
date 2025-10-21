// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.util.applyIf
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.components.evaluate
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isCharType
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.getSingleUnwrappedStatementOrThis
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.ConvertTwoComparisonsToRangeCheckInspection.Context
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.utils.canUseRangeUntil
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.utils.isFloatingPointType
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.utils.isIntegralType
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.utils.isSignedIntegralType
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.utils.isUnsignedIntegralType
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isSimplifiableTo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.renderAsEscapeSequence
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class ConvertTwoComparisonsToRangeCheckInspection : KotlinApplicableInspectionBase.Simple<KtBinaryExpression, Context>() {
    override fun getProblemDescription(element: KtBinaryExpression, context: Context) =
        KotlinBundle.message("two.comparisons.should.be.converted.to.a.range.check")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        binaryExpressionVisitor { visitTargetElement(it, holder, isOnTheFly) }

    private data class ComparisonData(
        val lesser: KtExpression,
        val greater: KtExpression,
        val strict: Boolean,
    ) {
        /**
         * Checks if this comparison forms the lower bound of a check pair, where the
         * upper bound of the check pair is [other].
         */
        fun isLowBoundFor(other: ComparisonData): Boolean =
            // b in a..c -> a <= b && b <= c
            greater !is KtConstantExpression && greater.isSimplifiableTo(other.lesser)

        fun inverted() = copy(
            lesser = greater,
            greater = lesser,
            strict = !strict,
        )
    }

    private fun KtExpression.asComparison(): ComparisonData? {
        if (this !is KtBinaryExpression) return null
        val (lesser, greater) = when (operationToken) {
            KtTokens.LT, KtTokens.LTEQ -> left to right
            KtTokens.GT, KtTokens.GTEQ -> right to left
            else -> return null
        }

        return ComparisonData(
            lesser = lesser?.getSingleUnwrappedStatementOrThis() ?: return null,
            greater = greater?.getSingleUnwrappedStatementOrThis() ?: return null,
            strict = operationToken == KtTokens.LT || operationToken == KtTokens.GT,
        )
    }

    private fun KtExpression.isSimple() = this is KtConstantExpression || this is KtNameReferenceExpression

    private data class PsiContext(
        val value: KtExpression,
        val min: KtExpression,
        val minExclusive: Boolean,
        val max: KtExpression,
        val maxExclusive: Boolean,
        val isInverseCheck: Boolean,
    )

    private fun KtBinaryExpression.getPsiContext(): PsiContext? {
        val isInverseCheck = when (operationToken) {
            KtTokens.ANDAND -> false
            KtTokens.OROR -> true
            else -> return null
        }

        val leftComparison = left?.asComparison()?.applyIf(isInverseCheck) { inverted() } ?: return null
        val rightComparison = right?.asComparison()?.applyIf(isInverseCheck) { inverted() } ?: return null

        val (lowBound, highBound) = when {
            leftComparison.isLowBoundFor(rightComparison) -> leftComparison to rightComparison
            rightComparison.isLowBoundFor(leftComparison) -> rightComparison to leftComparison
            else -> return null
        }

        val min = lowBound.lesser
        val max = highBound.greater
        if (!min.isSimple() || !max.isSimple()) return null


        return PsiContext(
            value = lowBound.greater,
            min = min,
            minExclusive = lowBound.strict,
            max = max,
            maxExclusive = highBound.strict,
            isInverseCheck = isInverseCheck,
        )
    }

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean =
        element.getPsiContext() != null

    internal data class Context(
        val value: SmartPsiElementPointer<KtExpression>,
        val minExpressionText: String,
        val maxExpressionText: String,
        val rangeOperator: String,
        val inverted: Boolean,
    ) {

        private val inOperator: String
            get() = if (inverted) "!in" else "in"

        fun createDisplayText(): String =
            "${inOperator} ${minExpressionText}${rangeOperator}${maxExpressionText}"
        fun createExpression(): KtBinaryExpression? {
            val factory = KtPsiFactory(value.project)
            val valuePsi = value.element?.copied() ?: return null
            val minPsi = factory.createExpression(minExpressionText)
            val maxPsi = factory.createExpression(maxExpressionText)

            return factory
                .createExpressionByPattern("$0 $inOperator $1$rangeOperator$2", valuePsi, minPsi, maxPsi)
                    as? KtBinaryExpression
        }
    }

    context(_: KaSession)
    private fun KtExpression.renderConstantPlusOne(expressionType: KaType): String? {
        val constant = evaluate() ?: return null

        if (constant is KaConstantValue.CharValue) {
            return "'${(constant.value + 1).renderAsEscapeSequence()}'"
        }

        val text = this.text
        val (prefix, radix) = when {
            text.startsWith("0x") -> "0x" to 16
            text.startsWith("0b") -> "0b" to 2
            else -> "" to 10
        }

        return if (expressionType.isUnsignedIntegralType) {
            val uLongValue: ULong = when (constant) {
                is KaConstantValue.ULongValue -> constant.value
                is KaConstantValue.UIntValue -> constant.value.toULong()
                is KaConstantValue.UShortValue -> constant.value.toULong()
                is KaConstantValue.UByteValue -> constant.value.toULong()
                else -> return null
            }

            "${prefix}${(uLongValue + 1u).toString(radix)}u"
        } else if (expressionType.isSignedIntegralType) {
            val longValue = (constant.value as? Number)?.toLong() ?: return null
            "${prefix}${(longValue + 1).toString(radix)}"
        } else {
            null
        }
    }

    context(_: KaSession)
    private fun KtExpression.adjustLowerBoundForExclusive(): String? {
        val type = expressionType ?: return null
        if (!type.isIntegralType && !type.isCharType) return null

        // Try to render an incremented constant value.
        renderConstantPlusOne(type)?.let { return it }

        // If we can't render the value as a constant, create ($this + 1) as an
        // expression instead.
        return KtPsiFactory(this.project)
            .createExpressionByPattern("($0 + 1)", this.copied())
            .text
    }

    context(_: KaSession)
    private fun KtExpression.asDoubleConstantExpression(): KtExpression? {
        val constantVal = evaluate()?.value as? Number ?: return null
        return KtPsiFactory(project).createExpression(constantVal.toDouble().toString())
    }

    override fun KaSession.prepareContext(element: KtBinaryExpression): Context? {
        val psiContext = element.getPsiContext() ?: return null

        val valueType = psiContext.value.expressionType?.lowerBoundIfFlexible() ?: return null
        if (!valueType.isSubtypeOf(StandardClassIds.Comparable)) return null

        var min = psiContext.min
        var max = psiContext.max

        val minType = min.expressionType?.lowerBoundIfFlexible() ?: return null
        val maxType = max.expressionType?.lowerBoundIfFlexible() ?: return null

        if (!valueType.semanticallyEquals(minType) || !valueType.semanticallyEquals(maxType)) {
            // Our expressions aren't all the same type. This is normally not allowed for a range check,
            // but some numeric checks can be semantically equivalent.

            val canCompare = when {
                // Signed integral types can compare with any other signed integral type.
                valueType.isSignedIntegralType -> minType.isSignedIntegralType && maxType.isSignedIntegralType
                // Ditto unsigned.
                valueType.isUnsignedIntegralType -> minType.isUnsignedIntegralType && maxType.isUnsignedIntegralType
                // For floats, we can compare to other floats, or, as a special case for comparing to constants with
                // a missing ".0" or "f" prefix, a version of that constant with the correct suffix added.
                valueType.isFloatingPointType -> {
                    val minCompatible =
                        minType.isFloatingPointType ||
                                (minType.isSignedIntegralType &&
                                        min.asDoubleConstantExpression()?.also { min = it } != null)
                    val maxCompatible =
                        maxType.isFloatingPointType ||
                                (maxType.isSignedIntegralType &&
                                        max.asDoubleConstantExpression()?.also { max = it } != null)

                    minCompatible && maxCompatible
                }

                else -> false
            }

            if (!canCompare) return null
        }

        val minText: String =
            // If we're using an exclusive comparison, the lower bound has to be increased by 1.
            if (psiContext.minExclusive) {
                if (!valueType.isIntegralType && !valueType.isCharType) return null
                min.adjustLowerBoundForExclusive() ?: return null
            } else {
                min.text
            }

        // For an exclusive upper bound of the range, we can use `..<` or `until`.
        val rangeOp = when {
            !psiContext.maxExclusive -> ".."
            element.canUseRangeUntil() -> "..<"
            valueType.isIntegralType || valueType.isCharType -> " until "
            else -> return null
        }

        val context = Context(
            psiContext.value.createSmartPointer(),
            minExpressionText = minText,
            maxExpressionText = max.text,
            rangeOperator = rangeOp,
            inverted = psiContext.isInverseCheck
        )

        // Final check - ensure that we don't accidentally create an infinite recursive loop by suggesting this fix within
        // the actual `contains` implementation of a range!
        val containingFunction = element.getStrictParentOfType<KtNamedFunction>()
        if (containingFunction != null
            && containingFunction.hasModifier(KtTokens.OPERATOR_KEYWORD)
            && containingFunction.nameAsName == OperatorNameConventions.CONTAINS) {
            val replacementExpression = context.createExpression() ?: return null
            val fragment =
                KtPsiFactory(containingFunction.project).createExpressionCodeFragment(replacementExpression.text, element)
            val fragmentExpression = fragment.getContentElement() as? KtBinaryExpression ?: return null
            analyze(fragment) {
                val resolvedSymbol =
                    fragmentExpression.operationReference.resolveToCall()?.singleFunctionCallOrNull()?.symbol
                        ?: return null

                if (resolvedSymbol == containingFunction.symbol) {
                    return null
                }
            }
        }

        return context
    }

    override fun createQuickFix(
        element: KtBinaryExpression,
        context: Context
    ): KotlinModCommandQuickFix<KtBinaryExpression> =
        ConvertTwoComparisonsToRangeCheckQuickFix(context)
}

internal class ConvertTwoComparisonsToRangeCheckQuickFix(private val context: Context) : KotlinModCommandQuickFix<KtBinaryExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("convert.to.a.range.check")

    override fun getName(): String =
        KotlinBundle.message("convert.comparisons.to.range.check", context.createDisplayText())

    override fun applyFix(
        project: Project,
        element: KtBinaryExpression,
        updater: ModPsiUpdater
    ) {
        val replacement = context.createExpression() ?: return
        element.replace(replacement)
    }
}