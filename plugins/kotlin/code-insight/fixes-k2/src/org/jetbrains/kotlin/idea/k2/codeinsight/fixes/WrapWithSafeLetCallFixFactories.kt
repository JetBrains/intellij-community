// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.compositeScope
import org.jetbrains.kotlin.analysis.api.components.scopeContext
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.core.FirKotlinNameSuggester
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.argumentIndex
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.types.expressions.OperatorConventions

internal object WrapWithSafeLetCallFixFactories {

    private val LOG = Logger.getInstance(this::class.java)

    private data class ElementContext(
        val nullableExpressionPointer: SmartPsiElementPointer<KtExpression>,
        val suggestedVariableName: String,
        val isImplicitInvokeCallToMemberProperty: Boolean,
    )

    /**
     *  Applicator that wraps a given target expression inside a `let` call on the input `nullableExpression`.
     *
     *  Consider the following code snippet:
     *
     *  ```
     *  fun test(s: String?) {
     *    println(s.length)
     *  }
     *  ```
     *
     *  In this case, one use the applicator with the following arguments
     *    - target expression: `s.length`
     *    - nullable expression: `s`
     *    - suggestedVariableName: `myName`
     *    - isImplicitInvokeCallToMemberProperty: false
     *
     *  Then the applicator changes the code to
     *
     *  ```
     *  fun test(s: String?) {
     *    println(s?.let { myName -> myName.length })
     *  }
     *  ```
     *  `isImplicitInvokeCallToMemberProperty` controls the behavior when hoisting up the nullable expression. It should be set to true
     *  if the call is to a invocable member property.
     */
    private class WrapWithSafeLetCallModCommandAction(
        element: KtExpression,
        elementContext: ElementContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtExpression, ElementContext>(element, elementContext) {

        override fun getFamilyName(): String = KotlinBundle.message("wrap.with.let.call")

        override fun invoke(
            actionContext: ActionContext,
            element: KtExpression,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            val nullableExpression =
                elementContext.nullableExpressionPointer.element?.let<KtExpression, KtExpression?>(updater::getWritable)
                    ?: return
            if (!nullableExpression.parents.contains(element)) {
                LOG.warn(
                    "Unexpected input for WrapWithSafeLetCall. Nullable expression '${nullableExpression.text}' should be a descendant" +
                            " of '${element.text}'."
                )
                return
            }

            val suggestedVariableName = elementContext.suggestedVariableName
            val psiFactory = KtPsiFactory(element.project)

            fun getNewExpression(nullableExpressionText: String, expressionUnderLetText: String): KtExpression {
                return when (suggestedVariableName) {
                    StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier -> psiFactory.createExpressionByPattern(
                        "$0?.let { $1 }",
                        nullableExpressionText,
                        expressionUnderLetText
                    )

                    else -> psiFactory.createExpressionByPattern(
                        "$0?.let { $1 -> $2 }",
                        nullableExpressionText,
                        suggestedVariableName,
                        expressionUnderLetText
                    )
                }
            }

            val callExpression = nullableExpression.parentOfType<KtCallExpression>(withSelf = true)
            val qualifiedExpression = callExpression?.getQualifiedExpressionForSelector()
            val receiverExpression = qualifiedExpression?.receiverExpression
            if (receiverExpression != null && elementContext.isImplicitInvokeCallToMemberProperty) {
                // In this case, the nullable expression is an invocable member. For example consider the following
                //
                // interface Foo {
                //   val bar: (() -> Unit)?
                // }
                // fun test(foo: Foo) {
                //   foo.bar()
                // }
                //
                // In this case, `foo.bar` is nullable and this fix should change the code to `foo.bar?.let { it() }`. But note that
                // the PSI structure of the above code is
                //
                // - qualifiedExpression: foo.bar()
                //   - receiver: foo
                //   - operationTokenNode: .
                //   - selectorExpression: bar()
                //     - calleeExpression: bar
                //     - valueArgumentList: ()
                //
                // So we need to explicitly construct the nullable expression text `foo.bar`.
                val nullableExpressionText =
                    "${receiverExpression.text}${qualifiedExpression.operationSign.value}${nullableExpression.text}"
                val newInvokeCallText =
                    "${suggestedVariableName}${callExpression.valueArgumentList?.text ?: ""}${
                        callExpression.lambdaArguments.joinToString(
                            " ",
                            prefix = " "
                        ) { it.text }
                    }"
                if (qualifiedExpression == element) {
                    element.replace(getNewExpression(nullableExpressionText, newInvokeCallText))
                } else {
                    qualifiedExpression.replace(psiFactory.createExpression(newInvokeCallText))
                    element.replace(getNewExpression(nullableExpressionText, element.text))
                }

            } else {
                val nullableExpressionText = when (nullableExpression) {
                    is KtBinaryExpression, is KtBinaryExpressionWithTypeRHS -> "(${nullableExpression.text})"
                    else -> nullableExpression.text
                }
                nullableExpression.replace(psiFactory.createExpression(suggestedVariableName))
                element.replace(getNewExpression(nullableExpressionText, element.text))
            }
        }
    }

    val forUnsafeCall = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnsafeCall ->
        val nullableExpression = diagnostic.receiverExpression
        createWrapWithSafeLetCallInputForNullableExpressionIfMoreThanImmediateParentIsWrapped(nullableExpression)
    }

    val forUnsafeImplicitInvokeCall = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnsafeImplicitInvokeCall ->
        val callExpression = diagnostic.psi.parentOfType<KtCallExpression>(withSelf = true)
            ?: return@ModCommandBased emptyList()
        val callingFunctionalVariableInLocalScope =
            isCallingFunctionalTypeVariableInLocalScope(callExpression)
                ?: return@ModCommandBased emptyList()

        createWrapWithSafeLetCallInputForNullableExpression(
            callExpression.calleeExpression,
            isImplicitInvokeCallToMemberProperty = !callingFunctionalVariableInLocalScope
        )
    }

    private fun KaSession.isCallingFunctionalTypeVariableInLocalScope(callExpression: KtCallExpression): Boolean? {
        val calleeExpression = callExpression.calleeExpression
        val calleeName = calleeExpression?.text?.let(Name::identifierIfValid) ?: return null
        val callSite = callExpression.parent as? KtQualifiedExpression ?: callExpression
        val functionalVariableSymbol = (calleeExpression.resolveToCall()?.singleCallOrNull<KaSimpleVariableAccessCall>())?.symbol ?: return false
        val localScope = callExpression.containingKtFile.scopeContext(callSite).compositeScope()
        // If no symbol in the local scope contains the called symbol, then the symbol must be a member symbol.

        return localScope.callables(calleeName).any { symbol ->
            symbol.psi?.let { it == functionalVariableSymbol.psi } == true
        }
    }

    val forUnsafeInfixCall = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnsafeInfixCall ->
        createWrapWithSafeLetCallInputForNullableExpressionIfMoreThanImmediateParentIsWrapped(diagnostic.receiverExpression)
    }

    val forUnsafeOperatorCall = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnsafeOperatorCall ->
        createWrapWithSafeLetCallInputForNullableExpressionIfMoreThanImmediateParentIsWrapped(diagnostic.receiverExpression)
    }

    val forArgumentTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        if (diagnostic.isMismatchDueToNullability) createWrapWithSafeLetCallInputForNullableExpression(diagnostic.psi.wrappingExpressionOrSelf)
        else emptyList()
    }

    private fun KaSession.createWrapWithSafeLetCallInputForNullableExpressionIfMoreThanImmediateParentIsWrapped(
        nullableExpression: KtExpression?,
        isImplicitInvokeCallToMemberProperty: Boolean = false,
    ): List<WrapWithSafeLetCallModCommandAction> {
        val surroundingExpression = nullableExpression?.surroundingExpression
        if (
            surroundingExpression == null ||
            // If the surrounding expression is at a place that accepts null value, then we don't provide wrap with let call because the
            // plain safe call operator (?.) is a better fix.
            isExpressionAtNullablePosition(surroundingExpression)
        ) {
            return emptyList()
        }
        // In addition, if there is no parent that is at a nullable position, then we don't offer wrapping with let either because
        // it still doesn't fix the code. Hence, the plain safe call operator is a better fix.
        val surroundingNullableExpression = findParentExpressionAtNullablePosition(nullableExpression) ?: return emptyList()
        return createWrapWithSafeLetCallInputForNullableExpression(
            nullableExpression,
            isImplicitInvokeCallToMemberProperty,
            surroundingNullableExpression
        )
    }

    context(session: KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun createWrapWithSafeLetCallInputForNullableExpression(
        nullableExpression: KtExpression?,
        isImplicitInvokeCallToMemberProperty: Boolean = false,
        surroundingExpression: KtExpression? = session.findParentExpressionAtNullablePosition(nullableExpression)
            ?: nullableExpression?.surroundingExpression,
    ): List<WrapWithSafeLetCallModCommandAction> {
        if (nullableExpression == null || surroundingExpression == null) return emptyList()
        val scope = nullableExpression.containingKtFile.scopeContext(nullableExpression).compositeScope()
        val existingNames = scope.getPossibleCallableNames().mapNotNull { it.identifierOrNullIfSpecial }

        // Note, the order of the candidate matters. We would prefer the default `it` so the generated code won't need to declare the
        // variable explicitly.
        val candidateNames = listOfNotNull(StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier, session.getDeclaredParameterNameForArgument(nullableExpression))

        val elementContext = ElementContext(
            nullableExpressionPointer = nullableExpression.createSmartPointer(),
            suggestedVariableName = FirKotlinNameSuggester.suggestNameByMultipleNames(candidateNames) { it !in existingNames },
            isImplicitInvokeCallToMemberProperty = isImplicitInvokeCallToMemberProperty,
        )
        return listOf(
            WrapWithSafeLetCallModCommandAction(surroundingExpression, elementContext),
        )
    }

    private fun KaSession.getDeclaredParameterNameForArgument(argumentExpression: KtExpression): String? {
        val valueArgument = argumentExpression.parent as? KtValueArgument ?: return null
        val callExpression = argumentExpression.parentOfType<KtCallExpression>()
        val successCallTarget = callExpression?.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return null

        return successCallTarget.valueParameters.getOrNull(valueArgument.argumentIndex)?.name?.identifierOrNullIfSpecial
    }

    private fun KaSession.findParentExpressionAtNullablePosition(expression: KtExpression?): KtExpression? {
        if (expression == null) return null
        var current = expression.surroundingExpression
        while (current != null && !isExpressionAtNullablePosition(current)) {
            current = current.surroundingExpression
        }
        return current
    }

    private fun KaSession.isExpressionAtNullablePosition(expression: KtExpression): Boolean {
        val parent = expression.parent
        return when {
            parent is KtProperty && expression == parent.initializer -> {
                if (parent.typeReference == null) return true
                val symbol = parent.symbol
                (symbol as? KaCallableSymbol)?.returnType?.isMarkedNullable ?: true
            }
            parent is KtValueArgument && expression == parent.getArgumentExpression() -> {
                // In the following logic, if call is missing, unresolved, or contains error, we just stop here so the wrapped call would be
                // inserted here.
                val functionCall = parent.getParentOfType<KtCallExpression>(strict = true) ?: return true
                val resolvedCall = functionCall.resolveToCall()?.singleFunctionCallOrNull() ?: return true
                return doesFunctionAcceptNull(resolvedCall, parent.argumentIndex) ?: true
            }
            parent is KtBinaryExpression -> {
                if (parent.operationToken in KtTokens.ALL_ASSIGNMENTS && parent.left == expression) {
                    // If current expression is an l-value in an assignment, just keep going up because one cannot assign to a let call.
                    return false
                }
                val resolvedCall = parent.resolveToCall()?.singleFunctionCallOrNull()
                when {
                    resolvedCall != null -> {
                        // The binary expression is a call to some function
                        val isInExpression = parent.operationToken in OperatorConventions.IN_OPERATIONS
                        val expressionIsArg = when {
                            parent.left == expression -> isInExpression
                            parent.right == expression -> !isInExpression
                            else -> return true
                        }
                        doesFunctionAcceptNull(resolvedCall, if (expressionIsArg) 0 else -1) ?: true
                    }
                    parent.operationToken == KtTokens.EQ -> {
                        // The binary expression is a variable assignment
                        parent.left?.expressionType?.isMarkedNullable ?: true
                    }
                    // The binary expression is some unrecognized constructs so we stop here.
                    else -> true
                }
            }
            // Qualified expression can always be updated with a safe call operator to make it accept nullable receiver. Hence, we
            // don't want to offer the wrap with let call quickfix.
            parent is KtQualifiedExpression && parent.receiverExpression == expression -> true
            // Ideally we should do more analysis on the control structure to determine if the type can actually allow null here. But that
            // may be too fancy and can be counter-intuitive to user.
            parent is KtContainerNodeForControlStructureBody -> true
            // Again, for simplicity's sake, we treat block as a place that can accept expression of any type. This is not strictly true
            // for lambda expressions, but it results in a more deterministic behavior.
            parent is KtBlockExpression -> true
            else -> false
        }
    }

    /**
     * Checks if the called function can accept null for the argument at the given index. If the index is -1, then we check the receiver
     * type. The function returns null if any necessary assumptions are not met. For example, if the call is not resolved to a unique
     * function or the function doesn't have a parameter at the given index. Then caller can do whatever needed to cover such cases.
     */
    private fun KaSession.doesFunctionAcceptNull(call: KaCall, index: Int): Boolean? {
        val symbol = (call as? KaFunctionCall<*>)?.symbol ?: return null
        if (index == -1) {
            // Null extension receiver means the function does not accept extension receiver and hence cannot be invoked on a nullable
            // value.
            return (symbol as? KaCallableSymbol)?.receiverType?.isMarkedNullable == true
        }
        return symbol.valueParameters.getOrNull(index)?.returnType?.isMarkedNullable
    }

    private val KtExpression.surroundingExpression: KtExpression?
        get() {
            var current: PsiElement? = parent
            while (true) {
                // Never go above declarations or control structure so that the wrap-with-let quickfix only applies to a "small" scope
                // around the nullable expression.
                if (current == null ||
                    current is KtContainerNodeForControlStructureBody ||
                    current is KtWhenEntry ||
                    current is KtParameter ||
                    current is KtProperty ||
                    current is KtReturnExpression ||
                    current is KtDeclaration ||
                    current is KtBlockExpression
                ) {
                    return null
                }
                val parent = current.parent
                if (current is KtExpression &&
                    // We skip parenthesized expression and labeled expressions.
                    current !is KtParenthesizedExpression && current !is KtLabeledExpression &&
                    // We skip KtCallExpression if it's the `selectorExpression` of a qualified expression because the selector expression is
                    // not an actual expression that can be swapped for any arbitrary expressions.
                    (parent !is KtQualifiedExpression || parent.selectorExpression != current)
                ) {
                    return current
                }
                current = parent
            }
        }

    private val PsiElement.wrappingExpressionOrSelf: KtExpression? get() = parentOfType(withSelf = true)
}