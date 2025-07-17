// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.psi.PsiElement
import com.intellij.util.applyIf
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeOwner
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeToken
import org.jetbrains.kotlin.analysis.api.lifetime.withValidityAssertion
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isPossiblySubTypeOf
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinAutoImportCallableWeigher
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
    context(KaSession)
    fun weigh(symbol: KaDeclarationSymbol): Int

    companion object {
        context(KaSession)
        fun createWeigher(element: PsiElement?): ExpressionImportWeigher =
            when (element) {
                is KtNameReferenceExpression -> CallExpressionImportWeigher(
                    token,
                    element,
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
                            parent.left?.expressionType,
                            parent.right?.expressionType,
                        )
                    } else OperatorExpressionImportWeigher(token, operatorName)
                }

                else -> Empty
            }

        private fun KaSession.calculateReceiverTypes(element: KtNameReferenceExpression): List<KaType> {
            val receiverExpression = element.getParentOfType<KtQualifiedExpression>(false)?.receiverExpression

            return if (receiverExpression != null) {
                val explicitType = receiverExpression.expressionType
                // use non-nullable type if safe call is used i.e `val value: T? = ...; value?.smth()
                val correctedExplicitType = explicitType?.applyIf(receiverExpression.parent is KtSafeQualifiedExpression) {
                    withNullability(KaTypeNullability.NON_NULLABLE)
                }
                listOfNotNull(correctedExplicitType)
            } else {
                element.containingKtFile.scopeContext(element).implicitReceivers.map { it.type }
            }
        }

        private fun KaSession.calculateValueArgumentTypes(element: KtNameReferenceExpression): List<KaType?> {
            val callExpression = element.getParentOfType<KtCallElement>(strict = false)
            val valueArgumentList = callExpression?.valueArgumentList ?: return emptyList()

            val valueArguments = valueArgumentList.arguments
            return valueArguments.map { it.getArgumentExpression()?.expressionType }
        }

        object Empty : ExpressionImportWeigher {
            context(KaSession)
            override fun weigh(symbol: KaDeclarationSymbol): Int = 0
        }
    }
}

internal abstract class AbstractExpressionImportWeigher : ExpressionImportWeigher {
    context(KaSession)
    override fun weigh(symbol: KaDeclarationSymbol): Int {
        val fqName = symbol.getFqNameIfPackageOrNonLocal()
        val baseWeight = fqName?.let { ImportFixHelper.calculateWeightBasedOnFqName(it, symbol.sourcePsi<PsiElement>()) } ?: 0

        return baseWeight + ownWeigh(symbol)
    }

    context(KaSession)
    protected abstract fun ownWeigh(symbol: KaDeclarationSymbol): Int

    context(KaSession)
    protected fun weighType(presentType: KaType, typeFromImport: KaType, baseWeight: Int): Int? {
        val adjustedType: KaType
        val nullablesWeight = if (presentType.nullability.isNullable == typeFromImport.nullability.isNullable) {
            adjustedType = presentType
            2
        } else {
            adjustedType = presentType.applyIf(presentType.nullability.isNullable) { withNullability(KaTypeNullability.NON_NULLABLE) }
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
    override val token: KaLifetimeToken,
    // the weigher is not saved in any context/state, and weigh() is called when element is still valid
    private val element: KtNameReferenceExpression,
    private val presentReceiverTypes: List<KaType>,
    private val valueArgumentTypes: List<KaType?>,
) : AbstractExpressionImportWeigher(), KaLifetimeOwner {

    context(KaSession)
    override fun ownWeigh(symbol: KaDeclarationSymbol): Int = withValidityAssertion {
        when {
            symbol is KaCallableSymbol -> calculateWeight(symbol, presentReceiverTypes, valueArgumentTypes)
            symbol is KaClassSymbol -> calculateWeight(symbol, valueArgumentTypes)
            symbol is KaTypeAliasSymbol -> -1
            else -> 0
        }
    }

    private fun KaSession.calculateWeight(
        symbolToBeImported: KaCallableSymbol,
        presentReceiverTypes: List<KaType>,
        presentValueArgumentTypes: List<KaType?>,
    ): Int {
        var weight = 0

        val receiverTypeFromImport = symbolToBeImported.receiverType
        if (receiverTypeFromImport != null) {
            weight = presentReceiverTypes.mapNotNull { weighType(it, receiverTypeFromImport, weight) }.maxOrNull() ?: weight
        }

        if (symbolToBeImported is KaFunctionSymbol) {
            val symbolValueParameters = symbolToBeImported.valueParameters
            val symbolHasVarargParameter = symbolValueParameters.any { it.isVararg }
            weight += if (symbolValueParameters.size >= presentValueArgumentTypes.size || symbolHasVarargParameter) {
                // same number of arguments is better than bigger number
                if (symbolValueParameters.size == presentValueArgumentTypes.size || symbolHasVarargParameter) 1 else 0
            } else {
                // apply only base weight if target has fewer parameters than expected
                return weight
            }

            // apply weighing extensions
            weight += calculateCallExtensionsWeight(symbolToBeImported)

            // TODO: it does not cover the following cases:
            //  - named parameters
            //  - default value, e.g. `param: Int = ""`
            //  - functional types, e.g. `Int.() -> Unit`
            //  - functional references, e.g. `::foo`

            val valueParametersFromImportIterator = symbolValueParameters.iterator()
            var valueParameterFromImport: KaValueParameterSymbol? = null

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

                if (updatedWeight == null && valueParameterFromImport.isVararg && valueArgumentType.isArrayOrPrimitiveArray) {
                    val arrayElementType = valueArgumentType.arrayElementType ?: break
                    updatedWeight = weighType(arrayElementType, valueParameterType, weight)
                }

                weight = updatedWeight ?: break
            }
        }
        return weight
    }

    private fun KaSession.calculateWeight(
        symbolToBeImported: KaClassSymbol,
        presentValueArgumentTypes: List<KaType?>,
    ): Int {
        // TODO: some constructors could be not visible
        val constructors = symbolToBeImported.declaredMemberScope.constructors
        constructors
            .filter { it.psi is KtFunction } // Filter out default constructors that don't have their own PSI.
            .maxOfOrNull { calculateWeight(it, presentReceiverTypes = emptyList(), presentValueArgumentTypes) }
            ?.let { return it }

        // In some cases (eg with an interface), there are no constructors. Weigh the class instead.
        return calculateCallExtensionsWeight(symbolToBeImported)
    }

    private fun KaSession.calculateCallExtensionsWeight(symbolToBeImported: KaCallableSymbol): Int =
        with(KotlinAutoImportCallableWeigher) { weigh(symbolToBeImported, element) }

    private fun KaSession.calculateCallExtensionsWeight(symbolToBeImported: KaClassSymbol): Int =
        with(KotlinAutoImportCallableWeigher) { weigh(symbolToBeImported, element) }
}

internal class OperatorExpressionImportWeigher(
    override val token: KaLifetimeToken,
    private val operatorName: Name?,
    private val leftOperandType: KaType? = null,
    private val rightOperandType: KaType? = null,
) : AbstractExpressionImportWeigher(), KaLifetimeOwner {

    context(KaSession)
    override fun weigh(symbol: KaDeclarationSymbol): Int {
        val functionSymbol = (symbol as? KaNamedFunctionSymbol)?.takeIf { it.isOperator } ?: return 0

        return super.weigh(functionSymbol)
    }

    context(KaSession)
    override fun ownWeigh(symbol: KaDeclarationSymbol): Int = withValidityAssertion {
        symbol as KaNamedFunctionSymbol

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