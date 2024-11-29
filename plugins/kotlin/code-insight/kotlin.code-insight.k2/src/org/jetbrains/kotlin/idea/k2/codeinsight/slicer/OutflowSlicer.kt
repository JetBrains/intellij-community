// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.slicer

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector.Access
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.parentOfType
import com.intellij.slicer.SliceUsage
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.*
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.codeInsight.slicer.AbstractKotlinSliceUsage
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.actualsForExpected
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinReadWriteAccessDetector
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex

class OutflowSlicer(
    element: KtElement, processor: Processor<in SliceUsage>, parentUsage: AbstractKotlinSliceUsage
) : Slicer(element, processor, parentUsage) {

    override fun processChildren(forcedExpressionMode: Boolean) {
        if (forcedExpressionMode) {
            (element as? KtExpression)?.let { processExpression(it) }
            return
        }

        when (element) {
            is KtProperty -> processVariable(element)

            is KtParameter -> processVariable(element)

            is KtFunction -> processFunction(element)

            is KtPropertyAccessor -> {
                if (element.isGetter) {
                    processVariable(element.property)
                }
            }

            is KtTypeReference -> {
                val declaration = element.parent
                require(declaration is KtCallableDeclaration)
                require(element == declaration.receiverTypeReference)

                if (declaration.isExpectDeclaration()) {
                    declaration.actualsForExpected().forEach {
                        (it as? KtCallableDeclaration)?.receiverTypeReference?.passToProcessor()
                    }
                }

                when (declaration) {
                    is KtFunction -> {
                        processExtensionReceiverUsages(declaration, declaration.bodyExpression, mode)
                    }

                    is KtProperty -> { //TODO: process only one of them or both depending on the usage type
                        processExtensionReceiverUsages(declaration, declaration.getter?.bodyExpression, mode)
                        processExtensionReceiverUsages(declaration, declaration.setter?.bodyExpression, mode)
                    }
                }
            }

            is KtExpression -> processExpression(element)
        }
    }

    private fun processVariable(variable: KtCallableDeclaration) {
        val withDereferences = parentUsage.params.showInstanceDereferences
        val accessKind = if (withDereferences) AccessKind.READ_OR_WRITE else AccessKind.READ_ONLY

        fun processVariableAccess(usageInfo: UsageInfo) {
            val refElement = usageInfo.element ?: return
            if (refElement !is KtExpression) {
                if (refElement.parentOfType<PsiComment>() == null) {
                    refElement.passToProcessor()
                }
                return
            }

            if (refElement.parent is KtValueArgumentName) return // named argument reference is not a read or write

            val refExpression = KtPsiUtil.safeDeparenthesize(refElement)
            if (withDereferences) {
                refExpression.processDereferences()
            }
            if (!withDereferences || KotlinReadWriteAccessDetector.INSTANCE.getExpressionAccess(refExpression) == Access.Read) {
                refExpression.passToProcessor()
            }
        }

        var searchScope = analysisScope

        if (variable is KtParameter) {
            if (!canProcessParameter(variable)) return //TODO

            val callable = variable.ownerFunction as? KtCallableDeclaration

            if (callable != null) {
                if (callable.isExpectDeclaration()) {
                    variable.actualsForExpected().forEach { it.passToProcessor() }
                }

                val parameterIndex = variable.parameterIndex()
                KotlinFindUsagesSupport.searchOverriders(callable, searchScope = analysisScope).forEach { overridingMember ->
                    when (overridingMember) {
                        is KtCallableDeclaration -> {
                            val parameters = overridingMember.valueParameters
                            check(parameters.size == callable.valueParameters.size)
                            parameters[parameterIndex].passToProcessor()
                        }

                        is PsiMethod -> {
                            val parameters = overridingMember.parameterList.parameters
                            val shift = if (callable.receiverTypeReference != null) 1 else 0
                            check(parameters.size == callable.valueParameters.size + shift)
                            parameters[parameterIndex + shift].passToProcessor()
                        }

                        else -> { // not supported
                        }
                    }
                    true
                }

                if (callable is KtNamedFunction) { // references to parameters of inline function can be outside analysis scope
                    searchScope = LocalSearchScope(callable)
                }
            }
        }

        processVariableAccesses(variable, searchScope, accessKind, ::processVariableAccess)
    }

    private fun processFunction(function: KtFunction) {
        processCalls(function, includeOverriders = false, CallSliceProducer)
    }

    private fun processExpression(expression: KtExpression) {
        val expressionWithValue = when (expression) {
            is KtFunctionLiteral -> expression.parent as KtLambdaExpression
            else -> expression
        }
        if (expressionWithValue is KtCallableReferenceExpression) {
            val callExpression = (expressionWithValue.parent as? KtParenthesizedExpression)?.parent as? KtCallExpression
            if (mode.currentBehaviour is LambdaCallsBehaviour) {
                val newMode = mode.dropBehaviour()
                callExpression?.passToProcessor(newMode)
            }
        }

        if (expressionWithValue is KtBinaryExpression) {
            val left = expressionWithValue.left
            if (left != null) {
                val access = KotlinReadWriteAccessDetector.INSTANCE.getExpressionAccess(left)
                if (access != Access.Read) {
                    left.mainReference?.resolve()?.passToProcessor()
                }
            }
        }
        val parent = expressionWithValue.parent
        when (parent) {

            is KtUnaryExpression -> {
                val elementType = parent.operationReference.getReferencedNameElementType()
                if (elementType == KtTokens.EXCLEXCL) {
                    parent.passToProcessorAsValue()
                } else {
                    (parent.operationReference.mainReference.resolve() as? KtCallableDeclaration)?.receiverTypeReference?.passToProcessor()
                }
            }

            is KtBinaryExpressionWithTypeRHS -> {
                val operationToken = parent.operationReference.getReferencedNameElementType()
                if (operationToken == KtTokens.AS_SAFE || operationToken == KtTokens.AS_KEYWORD || operationToken == KtTokens.IS_KEYWORD) {
                    parent.passToProcessorAsValue()
                }
            }

            is KtSafeQualifiedExpression -> {
                if (parent.receiverExpression == expressionWithValue) {
                    val selectorExpression = parent.selectorExpression ?: return
                    processSelectorExpression(selectorExpression, expressionWithValue)
                } else {
                    processExpression(parent)
                }
            }

            is KtDotQualifiedExpression -> {
                if (parent.receiverExpression == expressionWithValue) {
                    val selectorExpression = parent.selectorExpression ?: return
                    processSelectorExpression(selectorExpression, expressionWithValue)
                } else {
                    processExpression(parent)
                }
            }

            is KtCallExpression -> {
                processSelectorExpression(parent, expressionWithValue)
            }

            is KtValueArgument -> {
                val callExpression = parent.parentOfType<KtCallExpression>() ?: return
                analyze(callExpression) {
                    val functionCall = callExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return
                    val parameterSymbol =
                        functionCall.argumentMapping.filter { entry -> entry.key == expressionWithValue }.values.firstOrNull()?.symbol
                    val functionSymbol = functionCall.symbol as? KaNamedFunctionSymbol
                    if (functionSymbol?.isBuiltinFunctionInvoke == true) {
                        processImplicitInvokeCall(
                            functionCall,
                            parameterSymbol?.let { functionSymbol.valueParameters.indexOf(parameterSymbol) } ?: 0)
                    } else {
                        parameterSymbol?.psi?.passToProcessorInCallMode(callExpression)
                    }
                }
            }

            is KtParenthesizedExpression -> {
                processExpression(parent)
            }

            is KtProperty -> {
                if (parent.initializer == expressionWithValue) {
                    parent.passToProcessor()
                }
            }

            is KtBinaryExpression -> {
                if (parent.right == expressionWithValue) {
                    parent.left?.mainReference?.resolve()?.passToProcessor()
                } else {
                    (parent.operationReference.mainReference.resolve() as? KtCallableDeclaration)?.receiverTypeReference?.passToProcessor()
                }
            }

            is KtReturnExpression -> {
                analyze(parent) {
                    val target = parent.targetSymbol?.psi
                    if (target is KtNamedFunction) {
                        val (newMode, callElement) = mode.popInlineFunctionCall(target)
                        if (newMode != null) {
                            callElement?.passToProcessor(newMode)
                            return
                        }
                    }

                    target?.passToProcessor()
                }
            }

            is KtArrayAccessExpression -> {
                (parent.mainReference.resolve() as? KtCallableDeclaration)?.receiverTypeReference?.passToProcessor()
            }

            is KtFunction, is KtPropertyAccessor -> {
                parent.passToProcessor()
            }

            is KtBlockExpression -> {
                (parent.parent as? KtFunctionLiteral)?.passToProcessor()
            }

            is KtWhenEntry -> {
                (parent.parent as? KtWhenExpression)?.let { processExpression(it) }
            }

            is KtContainerNodeForControlStructureBody -> {
                (parent.parent as? KtIfExpression)?.let { processExpression(it) }
            }
        }
    }

    fun processSelectorExpression(selectorExpression: KtExpression, expressionWithValue: KtExpression) {
        analyze(selectorExpression) {
            val functionalCall = selectorExpression.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()
            val symbol = functionalCall?.partiallyAppliedSymbol?.symbol
            if (symbol is KaNamedFunctionSymbol && symbol.isBuiltinFunctionInvoke) {
                if ((functionalCall.partiallyAppliedSymbol.dispatchReceiver as? KaExplicitReceiverValue)?.expression == expressionWithValue.safeDeparenthesize()) {
                    if (mode.currentBehaviour is LambdaCallsBehaviour) {
                        selectorExpression.passToProcessor(mode)
                    }
                } else if ((functionalCall.partiallyAppliedSymbol.extensionReceiver as? KaExplicitReceiverValue)?.expression == expressionWithValue.safeDeparenthesize()) {
                    val successfulVariableAccessCall =
                        (selectorExpression as? KtCallExpression)?.calleeExpression?.resolveToCall()
                            ?.successfulVariableAccessCall()
                    successfulVariableAccessCall?.symbol?.psi?.passToProcessor(mode.withBehaviour(LambdaReceiverInflowBehaviour))
                }
            } else {
                val callableDeclaration = symbol?.psi?.navigationElement as? KtCallableDeclaration
                callableDeclaration?.receiverTypeReference?.passToProcessorInCallMode(expressionWithValue, mode)
            }
        }
    }

    context(KaSession)
    private fun processImplicitInvokeCall(functionCall: KaCallableMemberCall<*, *>, idx: Int) {
        val receiverValue = functionCall.partiallyAppliedSymbol.dispatchReceiver as? KaExplicitReceiverValue ?: return
        var receiverType: KaType? = receiverValue.type
        var receiverExpression = receiverValue.expression
        if (receiverExpression is KtReferenceExpression && functionCall is KaSimpleFunctionCall && functionCall.isImplicitInvoke) {
            val target = receiverExpression.mainReference.resolve()
            if (target is KtCallableDeclaration) {
                receiverType = (target.symbol as? KaCallableSymbol)?.returnType
                receiverExpression = target
            }
        }
        if (receiverType !is KaFunctionType) return
        val offset = if (receiverType.hasReceiver) 1 else 0
        val parameterIndex = idx - offset
        val newMode = if (parameterIndex >= 0) mode.withBehaviour(LambdaParameterInflowBehaviour(parameterIndex))
        else mode.withBehaviour(LambdaReceiverInflowBehaviour)
        receiverExpression.passToProcessor(newMode)
    }

    private fun KtExpression.processDereferences() {
        if (!parentUsage.params.showInstanceDereferences) return

        val parent = parent
        analyze(this) {
            val callInfo = ((parent as? KtSafeQualifiedExpression)?.selectorExpression ?: parent as? KtExpression)?.resolveToCall()
            if (callInfo is KaSuccessCallInfo) {
                val call = callInfo.call
                val partiallyAppliedSymbol = when (call) {
                    is KaCallableMemberCall<*, *> -> call.partiallyAppliedSymbol
                    is KaCompoundVariableAccessCall, is KaCompoundArrayAccessCall -> call.compoundOperation.operationPartiallyAppliedSymbol
                }
                val expression = (partiallyAppliedSymbol.dispatchReceiver as? KaExplicitReceiverValue)?.expression
                if (expression == (this@processDereferences).safeDeparenthesize()) {
                    processor.process(KotlinSliceDereferenceUsage(this@processDereferences, parentUsage, mode))
                }
            }
        }
    }
}
