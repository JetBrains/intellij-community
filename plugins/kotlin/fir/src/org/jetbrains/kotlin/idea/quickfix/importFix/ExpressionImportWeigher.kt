// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.importFix

import com.intellij.psi.PsiElement
import com.intellij.util.applyIf
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KtLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.idea.quickfix.ImportFixHelper
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.types.expressions.OperatorConventions

/**
 * Implementation in K1: [org.jetbrains.kotlin.idea.actions.ExpressionWeigher]
 */
interface ExpressionImportWeigher {
    context(KtAnalysisSession)
    fun weigh(symbol: KtDeclarationSymbol): Int

    companion object {
        context(KtAnalysisSession)
        fun createWeigher(element: PsiElement?): ExpressionImportWeigher =
            when (element) {
                is KtNameReferenceExpression -> CallExpressionImportWeigher(
                    token,
                    calculateReceiverTypes(element),
                    calculateValueArgumentTypes(element),
                )

                is KtOperationReferenceExpression -> {
                    val operatorName = element.operationSignTokenType?.let { operationSignTokenType ->
                        OperatorConventions.getNameForOperationSymbol(operationSignTokenType, false, true)
                    }

                    val parent = element.parent
                    if (parent is KtBinaryExpression) {
                        OperatorExpressionImportWeigher(
                            token,
                            operatorName,
                            parent.left?.getKtType(),
                            parent.right?.getKtType(),
                        )
                    } else OperatorExpressionImportWeigher(token, operatorName)
                }

                else -> Empty
            }

        context(KtAnalysisSession)
        private fun calculateReceiverTypes(element: KtNameReferenceExpression): List<KtType> {
            val receiverExpression = element.getParentOfType<KtQualifiedExpression>(false)?.receiverExpression

            return if (receiverExpression != null) {
                val explicitType = receiverExpression.getKtType()
                // use non-nullable type if safe call is used i.e `val value: T? = ...; value?.smth()
                val correctedExplicitType = explicitType?.applyIf(receiverExpression.parent is KtSafeQualifiedExpression) {
                    withNullability(KtTypeNullability.NON_NULLABLE)
                }
                listOfNotNull(correctedExplicitType)
            } else {
                element.containingKtFile.getScopeContextForPosition(element).implicitReceivers.map { it.type }
            }
        }

        context(KtAnalysisSession)
        private fun calculateValueArgumentTypes(element: KtNameReferenceExpression): List<KtType?> {
            val callExpression = element.getParentOfType<KtCallElement>(strict = false)
            val valueArgumentList = callExpression?.valueArgumentList ?: return emptyList()

            val valueArguments = valueArgumentList.arguments
            return valueArguments.map { it.getArgumentExpression()?.getKtType() }
        }

        object Empty : ExpressionImportWeigher {
            context(KtAnalysisSession)
            override fun weigh(symbol: KtDeclarationSymbol): Int = 0
        }
    }
}

internal abstract class AbstractExpressionImportWeigher : ExpressionImportWeigher {
    context(KtAnalysisSession)
    override fun weigh(symbol: KtDeclarationSymbol): Int {
        val fqName = symbol.getFqNameIfPackageOrNonLocal()
        val baseWeight = fqName?.let { ImportFixHelper.calculateWeightBasedOnFqName(it, symbol.sourcePsi<PsiElement>()) } ?: 0

        return baseWeight + ownWeigh(symbol)
    }

    context(KtAnalysisSession)
    protected abstract fun ownWeigh(symbol: KtDeclarationSymbol): Int

    context(KtAnalysisSession)
    protected fun weighType(presentType: KtType, typeFromImport: KtType, baseWeight: Int): Int? {
        val adjustedType: KtType
        val nullablesWeight = if (presentType.nullability.isNullable == typeFromImport.nullability.isNullable) {
            adjustedType = presentType
            2
        } else {
            adjustedType = presentType.applyIf(presentType.nullability.isNullable) { withNullability(KtTypeNullability.NON_NULLABLE) }
            // no reason to make `typeFromImport` not nullable as `T` is a subtype of `T?`
            0
        }

        return if (adjustedType.isPossiblySubTypeOf(typeFromImport)) {
            100 * baseWeight + 10 + nullablesWeight
        } else {
            null
        }
    }
}

internal class CallExpressionImportWeigher(
    override val token: KtLifetimeToken,
    private val presentReceiverTypes: List<KtType>,
    private val valueArgumentTypes: List<KtType?>,
) : AbstractExpressionImportWeigher(), KtLifetimeOwner {

    context(KtAnalysisSession)
    override fun ownWeigh(symbol: KtDeclarationSymbol): Int = withValidityAssertion {
        when {
            symbol is KtCallableSymbol -> calculateWeight(symbol, presentReceiverTypes, valueArgumentTypes)
            // TODO: some constructors could be not visible
            symbol is KtClassOrObjectSymbol && presentReceiverTypes.isEmpty() -> {
                val constructors = symbol.getDeclaredMemberScope().getConstructors()
                constructors.maxOfOrNull { calculateWeight(it, presentReceiverTypes = emptyList(), valueArgumentTypes) } ?: 0
            }

            else -> 0
        }
    }

    context(KtAnalysisSession)
    private fun calculateWeight(
        symbolToBeImported: KtCallableSymbol,
        presentReceiverTypes: List<KtType>,
        presentValueArgumentTypes: List<KtType?>,
    ): Int {
        var weight = 0

        val receiverTypeFromImport = symbolToBeImported.receiverType
        if (receiverTypeFromImport != null) {
            weight = presentReceiverTypes.mapNotNull { weighType(it, receiverTypeFromImport, weight) }.maxOrNull() ?: weight
        }

        if (symbolToBeImported is KtFunctionLikeSymbol) {
            val symbolValueParameters = symbolToBeImported.valueParameters
            val symbolHasVarargParameter = symbolValueParameters.any { it.isVararg }
            weight += if (symbolValueParameters.size >= presentValueArgumentTypes.size || symbolHasVarargParameter) {
                // same number of arguments is better than bigger number
                if (symbolValueParameters.size == presentValueArgumentTypes.size || symbolHasVarargParameter) 1 else 0
            } else {
                // apply only base weight if target has fewer parameters than expected
                return weight
            }

            // TODO: it does not cover the following cases:
            //  - named parameters
            //  - default value, e.g. `param: Int = ""`
            //  - functional types, e.g. `Int.() -> Unit`
            //  - functional references, e.g. `::foo`

            val valueParametersFromImportIterator = symbolValueParameters.iterator()
            var valueParameterFromImport: KtValueParameterSymbol? = null

            // update weight each time argument type matches the type of parameter from import; break once a mismatch appears
            for (valueArgumentType in presentValueArgumentTypes) {
                if (!valueParametersFromImportIterator.hasNext() || valueArgumentType == null) {
                    break
                }
                // don't update current parameter from import if it's vararg
                // TODO: cover the case with vararg argument followed by named arguments
                if (valueParameterFromImport == null || !valueParameterFromImport.isVararg) {
                    valueParameterFromImport = valueParametersFromImportIterator.next()
                }
                val valueParameterType = valueParameterFromImport.returnType

                var updatedWeight = weighType(valueArgumentType, valueParameterType, weight)

                if (updatedWeight == null && valueParameterFromImport.isVararg && valueArgumentType.isArrayOrPrimitiveArray()) {
                    val arrayElementType = valueArgumentType.getArrayElementType() ?: break
                    updatedWeight = weighType(arrayElementType, valueParameterType, weight)
                }

                weight = updatedWeight ?: break
            }
        }
        return weight
    }
}

internal class OperatorExpressionImportWeigher(
    override val token: KtLifetimeToken,
    private val operatorName: Name?,
    private val leftOperandType: KtType? = null,
    private val rightOperandType: KtType? = null,
) : AbstractExpressionImportWeigher(), KtLifetimeOwner {

    context(KtAnalysisSession)
    override fun weigh(symbol: KtDeclarationSymbol): Int {
        val functionSymbol = (symbol as? KtFunctionSymbol)?.takeIf { it.isOperator } ?: return 0

        return super.weigh(functionSymbol)
    }

    context(KtAnalysisSession)
    override fun ownWeigh(symbol: KtDeclarationSymbol): Int = withValidityAssertion {
        symbol as KtFunctionSymbol

        val name = symbol.name

        var weight = 0
        if (name == operatorName) {
            weight += 8
        }
        if (leftOperandType != null) {
            weight = symbol.receiverType?.let { weighType(leftOperandType, it, weight) } ?: weight
        }
        if (rightOperandType != null) {
            weight = symbol.valueParameters.firstOrNull()?.returnType?.let { weighType(rightOperandType, it, weight) } ?: weight
        }

        return weight
    }
}