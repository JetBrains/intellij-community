// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:OptIn(KaNonPublicApi::class)

package org.jetbrains.kotlin.idea.k2.codeinsight.slicer

import com.intellij.psi.PsiCall
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.slicer.SliceUsage
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.jetbrains.kotlin.analysis.api.KaNonPublicApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaBackingFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinPropertyFindUsagesOptions
import org.jetbrains.kotlin.idea.base.searching.usages.processAllUsages
import org.jetbrains.kotlin.idea.codeInsight.slicer.AbstractKotlinSliceUsage
import org.jetbrains.kotlin.idea.codeinsight.utils.getFunctionLiteralByImplicitLambdaParameter
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.idea.refactoring.changeSignature.toValVar
import org.jetbrains.kotlin.idea.references.KtPropertyDelegationMethodsReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.readWriteAccessWithFullExpression
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.references.ReferenceAccess
import org.jetbrains.kotlin.util.OperatorNameConventions

class InflowSlicer(
    element: KtElement,
    processor: Processor<in SliceUsage>,
    parentUsage: AbstractKotlinSliceUsage
) : Slicer(element, processor, parentUsage) {

    override fun processChildren(forcedExpressionMode: Boolean) {
        if (forcedExpressionMode) {
            (element as? KtExpression)?.let { processExpression(it) }
            return
        }

        when (element) {
            is KtProperty -> processProperty(element)

            // include overriders only when invoked on the parameter declaration
            is KtParameter -> processParameter(parameter = element, includeOverriders = parentUsage.parent == null)

            is KtDeclarationWithBody -> element.processBody()

            is KtTypeReference -> {
                val parent = element.parent
                require(parent is KtCallableDeclaration)
                require(element == parent.receiverTypeReference)
                // include overriders only when invoked on receiver type in the declaration
                processCalls(parent, includeOverriders = parentUsage.parent == null, sliceProducer = ReceiverSliceProducer)
            }

            is KtExpression -> processExpression(element)
        }
    }

    private fun processProperty(property: KtProperty) {
        if (property.hasDelegateExpression()) {
            val reference = property.delegate?.references?.filterIsInstance<KtPropertyDelegationMethodsReference>()?.firstOrNull() ?: return
            reference.multiResolve(false).firstOrNull()?.element?.passToProcessor()
            return
        }

        property.initializer?.passToProcessor()

        property.getter?.processBody()

        val isDefaultGetter = property.getter?.bodyExpression == null
        val isDefaultSetter = property.setter?.bodyExpression == null

        if (isDefaultGetter) {
            if (isDefaultSetter) {
                property.processPropertyAssignments()
            } else {
                property.setter!!.processBackingFieldAssignments()
            }
        }
    }

    private fun processParameter(parameter: KtParameter, includeOverriders: Boolean) {
        if (!canProcessParameter(parameter)) return

        val function = parameter.ownerFunction ?: return

        if (function is KtPropertyAccessor && function.isSetter) {
            function.property.processPropertyAssignments()
            return
        }

        if (function is KtNamedFunction
            && function.name == OperatorNameConventions.SET_VALUE.asString()
            && function.hasModifier(KtTokens.OPERATOR_KEYWORD)
        ) {

            ReferencesSearch.search(function, analysisScope)
                .forEach(Processor { reference ->
                    if (reference is KtPropertyDelegationMethodsReference) {
                        val property = reference.element.parent as? KtProperty
                        property?.processPropertyAssignments()
                    }
                    true
                })
        }

        if (function is KtFunction) {
            val isExtension = analyze(function) {
                val symbol = parameter.symbol
                (symbol.containingSymbol as? KaCallableSymbol)?.receiverParameter != null
            }
            processCalls(function, includeOverriders, ArgumentSliceProducer(parameter.parameterIndex(), isExtension))
        }

        val valVar = parameter.valOrVarKeyword.toValVar()
        if (valVar != KotlinValVar.None) {
            val classOrObject = (parameter.ownerFunction as? KtPrimaryConstructor)?.getContainingClassOrObject()
            if (classOrObject is KtClass && classOrObject.isData()) {
                // Search usages of constructor parameter in form of named argument of call to "copy" function.
                // We will miss calls of "copy" with positional parameters but it's unlikely someone write such code.
                // Also, we will find named arguments of constructor calls but we need them anyway (already found above).
                val options = KotlinPropertyFindUsagesOptions(project).apply {
                    searchScope = analysisScope
                }
                //TODO: optimizations to search only in files where "copy" word is present and also not resolve anything except named arguments
                parameter.processAllUsages(options) { usageInfo ->
                    (((usageInfo.element as? KtNameReferenceExpression)
                        ?.parent as? KtValueArgumentName)
                        ?.parent as? KtValueArgument)
                        ?.getArgumentExpression()
                        ?.passToProcessorAsValue()
                }
            }

            if (valVar == KotlinValVar.Var) {
                processAssignments(parameter, analysisScope)
            }
        }
    }

    private fun processExpression(expression: KtExpression) {
        val lambda = when (expression) {
            is KtLambdaExpression -> expression.functionLiteral
            is KtNamedFunction -> expression.takeIf { expression.name == null }
            else -> null
        }
        val currentBehaviour = mode.currentBehaviour
        if (lambda != null) {
            when (currentBehaviour) {
                is LambdaResultInflowBehaviour -> {
                    lambda.passToProcessor(mode.dropBehaviour())
                }

                is LambdaParameterInflowBehaviour -> {
                    val valueParameters = lambda.valueParameters
                    if (valueParameters.isEmpty() && lambda is KtFunctionLiteral) {
                        if (currentBehaviour.parameterIndex == 0) {
                            lambda.implicitItUsages().forEach {
                                it.passToProcessor(mode.dropBehaviour())
                            }
                        }
                    } else {
                        valueParameters.getOrNull(currentBehaviour.parameterIndex)?.passToProcessor(mode.dropBehaviour())
                    }
                }

                is LambdaReceiverInflowBehaviour -> {
                    processExtensionReceiverUsages(lambda, lambda, mode.dropBehaviour())
                }
            }
            return
        }

        fun processBodyResults(expr: KtExpression) {
            if (expr is KtBlockExpression) {
                analyze(expr) {
                    computeExitPointSnapshot(expr.statements).defaultExpressionInfo?.expression?.passToProcessor()
                }
            } else {
                expr.passToProcessor()
            }
        }

        when (expression) {

            is KtParenthesizedExpression -> {
                processExpression(expression.expression ?: return)
            }

            is KtCallableReferenceExpression -> {
                val referencedDeclaration = expression.callableReference.mainReference.resolve() ?: return
                when (currentBehaviour) {
                    is LambdaResultInflowBehaviour -> {
                        referencedDeclaration.passToProcessor(mode.dropBehaviour())
                    }

                    is LambdaParameterInflowBehaviour -> {
                        val parameter = (referencedDeclaration as? KtCallableDeclaration)
                            ?.valueParameters?.getOrNull(currentBehaviour.parameterIndex)
                        parameter?.passToProcessor(mode.dropBehaviour())
                    }

                    is LambdaReceiverInflowBehaviour -> {
                        val parameter = (referencedDeclaration as? KtCallableDeclaration)
                            ?.valueParameters?.getOrNull(0)
                        parameter?.passToProcessor(mode.dropBehaviour())
                    }
                }
            }

            //ignore KtBinaryExpression though something like `a + 1` might be supported
            is KtBinaryExpressionWithTypeRHS -> {
                val operationToken = expression.operationReference.getReferencedNameElementType()
                if (operationToken == KtTokens.AS_SAFE || operationToken == KtTokens.AS_KEYWORD || operationToken == KtTokens.IS_KEYWORD) {
                    expression.left.passToProcessor()
                }
            }

            is KtDotQualifiedExpression -> {
                analyze(expression) {
                    val call = expression.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()
                    val symbol = call?.partiallyAppliedSymbol?.symbol
                    if (symbol is KaNamedFunctionSymbol && symbol.isBuiltinFunctionInvoke) {
                        (call.partiallyAppliedSymbol.dispatchReceiver as? KaExplicitReceiverValue)?.expression?.passToProcessorAsValue(mode.withBehaviour(LambdaResultInflowBehaviour))
                    } else {
                        ((symbol as? KaSyntheticJavaPropertySymbol)?.javaGetterSymbol ?: symbol)?.psi?.passDeclarationToProcessorWithOverriders()
                    }
                }
            }

            is KtCallExpression -> {
                analyze(expression) {
                    val call = expression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()
                    if (call is KaSimpleFunctionCall && call.isImplicitInvoke) {
                        (call.partiallyAppliedSymbol.dispatchReceiver as? KaExplicitReceiverValue)?.expression?.passToProcessorAsValue(mode.withBehaviour(LambdaResultInflowBehaviour))
                    } else {
                        call?.partiallyAppliedSymbol?.symbol?.psi?.passToProcessorInCallMode(expression, withOverriders = true)
                    }
                }
            }

            is KtSimpleNameExpression -> {
                val resolve = analyze(expression) {
                    val resolveToSymbol = expression.mainReference.resolveToSymbol()
                    when {
                      resolveToSymbol is KaBackingFieldSymbol -> {
                          val property = resolveToSymbol.owningProperty.psi as? KtProperty ?: return
                          val setter = property.setter
                          if (setter != null) {
                              setter.processBackingFieldAssignments()
                          } else {
                              property.processPropertyAssignments()
                          }
                          return
                      }
                      resolveToSymbol is KaValueParameterSymbol && resolveToSymbol.isImplicitLambdaParameter -> {
                          val isExtension = (resolveToSymbol.containingSymbol as? KaCallableSymbol)?.receiverParameter != null
                          resolveToSymbol.psi?.passToProcessorAsValue(mode.withBehaviour(LambdaCallsBehaviour(ArgumentSliceProducer(0, isExtension))))
                          return
                      }
                      else -> ((resolveToSymbol as? KaSyntheticJavaPropertySymbol)?.javaGetterSymbol ?: resolveToSymbol as? KaVariableSymbol)?.psi
                    }
                }

                resolve?.passToProcessor()
            }


            is KtIfExpression -> {
                expression.then?.let(::processBodyResults)
                expression.`else`?.let(::processBodyResults)
            }

            is KtWhenExpression -> {
                expression.entries.forEach { entry ->
                    entry.expression?.let(::processBodyResults)
                }
            }

            is KtTryExpression -> {
                analyze(expression) {
                    computeExitPointSnapshot(expression.tryBlock.statements).defaultExpressionInfo?.expression?.passToProcessor()
                }
            }

            is KtUnaryExpression -> {
                val elementType = expression.operationReference.getReferencedNameElementType()
                if (elementType == KtTokens.EXCLEXCL) {
                    expression.baseExpression?.passToProcessor()
                } else if (expression is KtPostfixExpression && (elementType == KtTokens.PLUSPLUS || elementType == KtTokens.MINUSMINUS)) {
                    expression.baseExpression?.mainReference?.resolve()?.passToProcessor()
                }
            }

            is KtThisExpression -> {
                val target = expression.instanceReference.mainReference.resolve()

                when (target) {
                    is KtFunctionLiteral -> {
                        target.passToProcessorAsValue(mode.withBehaviour(LambdaCallsBehaviour(ReceiverSliceProducer)))
                    }

                    is KtTypeReference -> {
                        target.passToProcessor()
                    }
                }
            }
        }
    }

    private fun KtProperty.processPropertyAssignments() {
        val accessSearchScope = if (isVar) {
            analysisScope
        } else {
            val containerScope = LocalSearchScope(getStrictParentOfType<KtDeclaration>() ?: return)
            analysisScope.intersectWith(containerScope)
        }
        processAssignments(this, accessSearchScope)
    }

    private fun processAssignments(variable: KtCallableDeclaration, accessSearchScope: SearchScope) {
        fun processVariableAccess(usageInfo: UsageInfo) {
            val refElement = usageInfo.element ?: return
            val refParent = refElement.parent

            val rhsValue = when {
                refElement is KtExpression -> {
                    val (accessKind, accessExpression) = refElement.readWriteAccessWithFullExpression(true)
                    if (accessKind == ReferenceAccess.WRITE &&
                        accessExpression is KtBinaryExpression &&
                        accessExpression.operationToken == KtTokens.EQ
                    ) {
                        accessExpression.right
                    } else {
                        accessExpression
                    }
                }

                refParent is PsiCall -> refParent.argumentList?.expressions?.getOrNull(0)

                else -> null
            }
            rhsValue?.passToProcessorAsValue()
        }

        processVariableAccesses(variable, accessSearchScope, AccessKind.WRITE_WITH_OPTIONAL_READ, ::processVariableAccess)
    }

    private fun KtPropertyAccessor.processBackingFieldAssignments() {
        forEachDescendantOfType<KtBinaryExpression> body@{
            if (it.operationToken != KtTokens.EQ) return@body
            val lhs = it.left?.let { expression -> KtPsiUtil.safeDeparenthesize(expression) } ?: return@body
            val rhs = it.right ?: return@body
            if (!lhs.isBackingFieldReference()) return@body
            rhs.passToProcessor()
        }
    }

    private fun KtExpression.isBackingFieldReference(): Boolean {
        return this is KtSimpleNameExpression &&
                getReferencedName() == KtTokens.FIELD_KEYWORD.value &&
                analyze(this) {
                    this@isBackingFieldReference.mainReference.resolveToSymbol() is KaBackingFieldSymbol
                }
    }

    @OptIn(KaNonPublicApi::class)
    private fun KtDeclarationWithBody.processBody() {
        val bodyExpression = bodyExpression ?: return
        if (bodyExpression is KtBlockExpression) {
            analyze(bodyExpression) {
                val returnExpressions = computeExitPointSnapshot(bodyExpression.statements).valuedReturnExpressions
                returnExpressions.forEach {
                    ((it as? KtReturnExpression)?.returnedExpression ?: it).passToProcessorAsValue()
                }
            }
        } else {
            bodyExpression.passToProcessorAsValue()
        }
    }

    override fun processCalls(callable: KtCallableDeclaration, includeOverriders: Boolean, sliceProducer: SliceProducer) {
        if (callable is KtNamedFunction) {
            val (newMode, callElement) = mode.popInlineFunctionCall(callable)
            if (newMode != null && callElement != null) {
                val sliceUsage = KotlinSliceUsage(callElement, parentUsage, newMode, false)
                sliceProducer.produceAndProcess(sliceUsage, newMode, parentUsage, processor)
                return
            }
        }

        super.processCalls(callable, includeOverriders, sliceProducer)
    }

    private fun KtFunctionLiteral.implicitItUsages(): Collection<KtSimpleNameExpression> {
        return collectDescendantsOfType(fun(expression: KtSimpleNameExpression): Boolean {
            if (expression.getQualifiedExpressionForSelector() != null || expression.getReferencedNameAsName() != StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME) return false
            val lBrace = (expression as? KtNameReferenceExpression)?.getFunctionLiteralByImplicitLambdaParameter()?.lBrace?.nextSibling
                ?: return false
            return lBrace == this.lBrace.node.treeNext.psi
        })
    }
}
