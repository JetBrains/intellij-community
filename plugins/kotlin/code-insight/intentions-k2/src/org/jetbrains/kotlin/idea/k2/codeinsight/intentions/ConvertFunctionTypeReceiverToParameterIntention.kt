// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allowAnalysisFromWriteActionInEdt
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidatorProvider
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinTypeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.KotlinBaseChangeSignatureUsage
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.KotlinNameSuggester
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getOrCreateParameterList
import org.jetbrains.kotlin.psi.typeRefHelpers.setReceiverTypeReference

class ConvertFunctionTypeReceiverToParameterIntention : SelfTargetingRangeIntention<KtTypeReference>(
    KtTypeReference::class.java, KotlinBundle.messagePointer("convert.function.type.receiver.to.parameter")
) {

    override fun startInWriteAction(): Boolean = false

    override fun applicabilityRange(element: KtTypeReference): TextRange? {
        val functionTypeReceiver = element.parent as? KtFunctionTypeReceiver ?: return null
        val functionType = functionTypeReceiver.parent as? KtFunctionType ?: return null

        val elementAfter = functionType.copied().apply {
            val receiver = receiverTypeReference ?: return null
            val parameterList = parameterList ?: return null
            val psiFactory = KtPsiFactory(project)
            val newParam = psiFactory.createFunctionTypeParameter(receiver)
            parameterList.addParameterBefore(newParam, parameterList.parameters.firstOrNull())
            setReceiverTypeReference(null)
        }

        setTextGetter(KotlinBundle.messagePointer("convert.0.to.1", functionType.text, elementAfter.text))
        return element.textRange
    }

    override fun applyTo(element: KtTypeReference, editor: Editor?) {
        val data = element.getConversionData() ?: return
        ReceiverToParameterConverter(data).run()
    }

    private fun KtTypeReference.getConversionData(): ReceiverToParameterConversionData? {
        val functionTypeReceiver = parent as? KtFunctionTypeReceiver ?: return null
        val functionType = functionTypeReceiver.parent as? KtFunctionType ?: return null

        val containingParameter = (functionType.parent as? KtTypeReference)?.parent as? KtParameter
        val ownerFunction =
            containingParameter?.ownerFunction as? KtFunction ?: (functionType.parent as? KtTypeReference)?.parent as? KtCallableDeclaration
            ?: return null

        val functionParameterIndex = if (containingParameter != null) ownerFunction.valueParameters.indexOf(containingParameter) else null

        val methodDescriptor = KotlinMethodDescriptor(ownerFunction)
        val changeInfo = KotlinChangeInfo(methodDescriptor)

        val functionTypeParameterList = functionType.parameterList ?: return null

        val newType = buildString {
            append("(")
            append(functionTypeReceiver.typeReference.text)
            if (functionTypeParameterList.parameters.isNotEmpty()) {
                if (functionTypeParameterList.parameters.first().text.isNotBlank()) {
                    append(", ")
                }
                append(functionTypeParameterList.parameters.joinToString { it.text })
            }
            append(") -> ")
            append(functionType.returnTypeReference?.text ?: "Unit")
        }

        if (functionParameterIndex != null) {
            changeInfo.newParameters[functionParameterIndex].setType(newType)
        } else {
            changeInfo.newReturnTypeInfo = KotlinTypeInfo(newType, ownerFunction)
        }

        return ReceiverToParameterConversionData(functionParameterIndex, changeInfo)
    }
}

internal class ReceiverToParameterConversionData(
    val functionParameterIndex: Int?,
    val changeInfo: KotlinChangeInfo,
)

internal class ReceiverToParameterConverter(
    private val data: ReceiverToParameterConversionData
) : KotlinChangeSignatureProcessor(data.changeInfo.method.project, data.changeInfo) {

    // under potemkin progress: it's ok to start analysis
    override fun findUsages(): Array<out UsageInfo?> {
        val baseUsages = super.findUsages()
        val usages = mutableListOf<UsageInfo>()

        for (info in baseUsages) {
            when (val element = info.element) {
                is KtCallableDeclaration -> {
                    // overridden usages
                    allowAnalysisFromWriteActionInEdt(element) {
                        processInternalUsages(element, usages)
                    }
                }

                is KtCallElement, is KtExpression -> {
                    allowAnalysisFromWriteActionInEdt(element) {
                        processExternalUsage(element, usages)
                    }
                }
            }
        }

        val method = data.changeInfo.method as KtCallableDeclaration
        allowAnalysisFromWriteActionInEdt(method) {
            processInternalUsages(method, usages)
        }

        return baseUsages + usages
    }

    context(_: KaSession)
    private fun processInternalUsages(callable: KtCallableDeclaration, usages: MutableList<UsageInfo>) {
        val functionParameterIndex = data.functionParameterIndex
        if (functionParameterIndex != null) {
            val body = when (callable) {
                is KtConstructor<*> -> callable.containingClassOrObject?.body ?: return
                is KtFunction -> callable.bodyBlockExpression ?: callable.bodyExpression ?: return
                else -> return
            }

            val functionParameter = callable.valueParameters.getOrNull(functionParameterIndex) ?: return
            for (ref in ReferencesSearch.search(functionParameter, LocalSearchScope(body)).asIterable()) {
                val element = ref.element as? KtSimpleNameExpression ?: continue
                val callExpression = element.getParentOfTypeAndBranch<KtCallExpression> { calleeExpression } ?: continue
                usages += ConvertQualifiedCallReceiverIntoArgumentUsage(callExpression)
            }
        } else {
            // change in return type
            when (callable) {
                is KtNamedFunction -> processBody(callable, usages, callable.bodyExpression)
                is KtProperty -> {
                    processBody(callable, usages, callable.initializer)
                    callable.getter?.let { processBody(it, usages, it.bodyExpression) }
                }

                else -> Unit
            }
        }
    }

    context(_: KaSession)
    private fun processBody(
        callable: KtElement,
        usages: MutableList<UsageInfo>,
        bodyExpression: KtExpression?,
    ) {

        when (val body = bodyExpression?.deparenthesized()) {
            is KtLambdaExpression -> {
                collectLambdaChanges(body, usages)
            }

            is KtBlockExpression -> {
                bodyExpression.forEachDescendantOfType<KtReturnExpression> { returnExpression ->
                    (returnExpression.returnedExpression?.deparenthesized() as? KtLambdaExpression)?.takeIf {
                            val targetLabel = returnExpression.getTargetLabel()
                            targetLabel == null || targetLabel.mainReference.resolve() == callable
                        }?.let { lambdaExpression ->
                            collectLambdaChanges(lambdaExpression, usages)
                        }
                }
            }

            else -> Unit
        }
    }

    private fun KtExpression.deparenthesized(): KtElement = KtPsiUtil.safeDeparenthesize(this)

    context(_: KaSession)
    private fun getArgumentExpressionToProcess(callElement: KtCallElement): KtExpression? {
        val argumentMapping = callElement.resolveToCall()?.successfulFunctionCallOrNull()?.argumentMapping ?: return null
        val parameter = (data.changeInfo.method as KtFunction).valueParameters[data.functionParameterIndex ?: return null]
        val entry = argumentMapping.entries.find { (_, value) -> value.name.asString() == parameter.name } ?: return null
        return KtPsiUtil.deparenthesize(entry.key)
    }

    context(_: KaSession)
    private fun processExternalUsage(refElement: PsiElement, usages: MutableList<in UsageInfo>) {
        val callElement = refElement as? KtCallElement ?: return
        val expressionToProcess = getArgumentExpressionToProcess(callElement) ?: return
        if (expressionToProcess is KtLambdaExpression) {
            collectLambdaChanges(expressionToProcess, usages)
        }
    }

    context(_: KaSession)
    private fun ReceiverToParameterConverter.collectLambdaChanges(
        expressionToProcess: KtLambdaExpression, usages: MutableList<in UsageInfo>
    ) {
        val newParamName = getNewParameterName(expressionToProcess)
        collectInnerChangesInLambda(expressionToProcess, newParamName, usages)
        val currentParametersSize = expressionToProcess.functionLiteral.symbol.valueParameters.size
        usages += ConvertFunctionalLambdaReceiverToParameterUsage(expressionToProcess, newParamName, currentParametersSize)
    }

    context(session: KaSession)
    private fun collectInnerChangesInLambda(
        lambda: KtLambdaExpression, newParameterName: String, usages: MutableList<in UsageInfo>
    ) {
        val lambdaSymbol = lambda.functionLiteral.symbol
        lambda.accept(object : KtTreeVisitorVoid() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                super.visitSimpleNameExpression(expression)
                if (expression is KtOperationReferenceExpression) return
                val resolvedCall =
                    expression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.partiallyAppliedSymbol ?: return
                val dispatchReceiverTarget = resolvedCall.dispatchReceiver?.getReceiverTargetSymbol()
                val extensionReceiverTarget = resolvedCall.extensionReceiver?.getReceiverTargetSymbol()
                if (dispatchReceiverTarget == lambdaSymbol.receiverParameter || extensionReceiverTarget == lambdaSymbol.receiverParameter) {
                    val parent = expression.parent
                    if (parent is KtCallExpression && expression == parent.calleeExpression) {
                        if ((parent.parent as? KtQualifiedExpression)?.receiverExpression !is KtThisExpression) {
                            usages.add(ConvertWithReplacement(parent, "$newParameterName.${parent.text}"))
                        }
                    } else if ((parent as? KtQualifiedExpression)?.receiverExpression !is KtThisExpression) {
                        val referencedName = expression.getReferencedName()
                        usages.add(ConvertWithReplacement(expression, "$newParameterName.$referencedName"))
                    }
                }
            }

            override fun visitThisExpression(expression: KtThisExpression) {
                val symbol = expression.instanceReference.mainReference.resolveToSymbol()

                if (symbol == lambdaSymbol.receiverParameter) {
                    usages.add(ConvertWithReplacement(expression, newParameterName))
                }
            }
        })
    }

    private class ConvertWithReplacement(element: KtElement, private val replacement: String) : UsageInfo(element),
                                                                                                KotlinBaseChangeSignatureUsage {
        override fun processUsage(
            changeInfo: KotlinChangeInfoBase, element: KtElement, allUsages: Array<out UsageInfo>
        ): KtElement? {
            return element.replace(KtPsiFactory.contextual(element).createExpression(replacement)) as KtElement?
        }
    }

    private class ConvertQualifiedCallReceiverIntoArgumentUsage(element: KtCallExpression) : UsageInfo(element),
                                                                                             KotlinBaseChangeSignatureUsage {
        override fun processUsage(changeInfo: KotlinChangeInfoBase, element: KtElement, allUsages: Array<out UsageInfo>): KtElement? {
            val callExpression = element as? KtCallExpression ?: return null
            val qualified = callExpression.getQualifiedExpressionForSelector() ?: return null
            val receiverExpression = qualified.receiverExpression

            val argumentList = callExpression.valueArgumentList ?: callExpression.addAfter(
                KtPsiFactory(project).createCallArguments("()"), callExpression.calleeExpression
            ) as KtValueArgumentList

            argumentList.addArgumentBefore(KtPsiFactory(project).createArgument(receiverExpression), argumentList.arguments.firstOrNull())
            return qualified.replace(callExpression) as KtElement
        }
    }

    private class ConvertFunctionalLambdaReceiverToParameterUsage(
        element: KtLambdaExpression, private val newParamName: String, private val currentParametersSize: Int
    ) : UsageInfo(element), KotlinBaseChangeSignatureUsage {
        override fun processUsage(changeInfo: KotlinChangeInfoBase, element: KtElement, allUsages: Array<out UsageInfo>): KtElement? {
            val lambda = element as? KtLambdaExpression ?: return null
            val psiFactory = KtPsiFactory(lambda.project)

            val parameterList = lambda.functionLiteral.getOrCreateParameterList()
            if (currentParametersSize > 0 && parameterList.parameters.isEmpty()) {
                // include explicitly `it`
                val implicitParam = psiFactory.createLambdaParameterList("it").parameters.first()
                parameterList.addParameterBefore(implicitParam, null)
            }
            val newParam = psiFactory.createLambdaParameterList(newParamName).parameters.first()
            return parameterList.addParameterBefore(newParam, parameterList.parameters.firstOrNull())
        }

    }
}

context(_: KaSession)
private fun getNewParameterName(
    expressionToProcess: KtLambdaExpression,
): String {
    val receiverType = (expressionToProcess.expressionType as? KaFunctionType)?.receiverType
    val validator = KotlinNameValidatorProvider.getInstance().createNameValidator(
            container = expressionToProcess,
            target = KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER,
            anchor = expressionToProcess,
        )
    val newParamName = if (receiverType != null) {
        KotlinNameSuggester.suggestNamesByType(receiverType, expressionToProcess, validator, "receiver").first()
    } else {
        "receiver"
    }
    return newParamName
}

context(_: KaSession)
fun KaReceiverValue?.getReceiverTargetSymbol(): KaSymbol? {
    return when (this) {
        is KaExplicitReceiverValue -> {
            val target = when (val expression = KtPsiUtil.deparenthesize(this.expression)) {
                is KtThisExpression -> expression.instanceReference
                is KtReferenceExpression -> expression
                else -> null
            }
            return (target as? KtDeclaration)?.symbol
        }

        is KaImplicitReceiverValue -> this.symbol
        else -> null
    }
}