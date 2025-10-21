// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.text.UniqueNameGenerator
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allowAnalysisFromWriteActionInEdt
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfo
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeInfoBase
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinChangeSignatureProcessor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.usages.KotlinBaseChangeSignatureUsage
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.KotlinNameSuggester
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.K2IntroduceVariableHandler
import org.jetbrains.kotlin.idea.k2.refactoring.moveFunctionLiteralOutsideParenthesesIfPossible
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.psi.psiUtil.getValueParameters
import org.jetbrains.kotlin.psi.typeRefHelpers.setReceiverTypeReference

class ConvertFunctionTypeParameterToReceiverIntention : SelfTargetingRangeIntention<KtTypeReference>(
    KtTypeReference::class.java, KotlinBundle.messagePointer("convert.function.type.parameter.to.receiver")
) {

    override fun startInWriteAction(): Boolean = false

    override fun applicabilityRange(element: KtTypeReference): TextRange? {
        val data = element.getConversionData() ?: return null

        val elementBefore =
            (data.changeInfo.method as KtFunction).valueParameters[data.functionParameterIndex].typeReference!!.typeElement as KtFunctionType
        val elementAfter = elementBefore.copied().apply {
            setReceiverTypeReference(element)
            parameterList!!.removeParameter(data.typeParameterIndex)
        }

        setTextGetter(KotlinBundle.messagePointer("convert.0.to.1", elementBefore.text, elementAfter.text))
        return element.textRange
    }

    override fun applyTo(element: KtTypeReference, editor: Editor?) {
        element.getConversionData()?.let { Converter(it).run() }
    }

    private fun KtTypeReference.getConversionData(): ConversionData? {
        val parameter = parent as? KtParameter ?: return null
        val functionType = parameter.getParentOfTypeAndBranch<KtFunctionType> { parameterList } ?: return null
        if (functionType.receiverTypeReference != null) return null
        val containingParameter = (functionType.parent as? KtTypeReference)?.parent as? KtParameter ?: return null
        val ownerFunction = containingParameter.ownerFunction as? KtFunction ?: return null
        val typeParameterIndex = functionType.parameters.indexOf(parameter)
        val functionParameterIndex = ownerFunction.valueParameters.indexOf(containingParameter)

        val methodDescriptor = KotlinMethodDescriptor(ownerFunction)

        val changeInfo = KotlinChangeInfo(methodDescriptor)

        val functionTypeParameterList = functionType.parameterList ?: return null

        val newType = buildString {
            append(functionTypeParameterList.parameters[typeParameterIndex].typeReference!!.text)
            append(".(")
            append(functionTypeParameterList.parameters.withIndex().filter { it.index != typeParameterIndex }.joinToString { it.value.text })
            append(") -> ")
            append(functionType.returnTypeReference?.text ?: "Unit")
        }

        changeInfo.newParameters[functionParameterIndex].setType(newType)

        return ConversionData(typeParameterIndex, functionParameterIndex, changeInfo)
    }
}

private class ConversionData(
    val typeParameterIndex: Int,
    val functionParameterIndex: Int,
    val changeInfo: KotlinChangeInfo,
) {
    val isFirstParameter: Boolean get() = typeParameterIndex == 0
}


private class Converter(
    private val data: ConversionData
) : KotlinChangeSignatureProcessor(data.changeInfo.method.project, data.changeInfo) {

    override fun findUsages(): Array<out UsageInfo?> {
        val baseUsages = super.findUsages()
        val usages = mutableListOf<UsageInfo>()
        for (info in baseUsages) {
            when (val element = info.element) {
                is KtFunction -> {
                    processInternalUsages(element, usages)
                }

                is KtCallElement, is KtExpression -> {
                    allowAnalysisFromWriteActionInEdt(element) {
                        processExternalUsage(element, usages)
                    }
                }

                !is KtElement if element != null && !data.isFirstParameter -> {
                    usages.add(
                        ConflictUsageInfo(
                            element, KotlinBundle.message(
                                "can.t.replace.non.kotlin.reference.with.call.expression.0", StringUtil.htmlEmphasize(element.text)
                            )
                        )
                    )
                }
            }
        }
        processInternalUsages(data.changeInfo.method as KtFunction, usages)
        return baseUsages + usages
    }

    context(_: KaSession) private fun processExternalUsage(
        refElement: PsiElement,
        usages: MutableList<in UsageInfo>,
    ) {
        val callElement = refElement as? KtCallElement
        if (callElement != null) {
            val expressionToProcess = getArgumentExpressionToProcess(callElement) ?: return

            if (!data.isFirstParameter && callElement is KtConstructorDelegationCall && expressionToProcess !is KtLambdaExpression && expressionToProcess !is KtSimpleNameExpression && expressionToProcess !is KtCallableReferenceExpression) {
                usages.add(
                    ConflictUsageInfo(
                        expressionToProcess, KotlinBundle.message(
                            "following.expression.won.t.be.processed.since.refactoring.can.t.preserve.its.semantics.0",
                            expressionToProcess.text
                        )
                    )
                )
                return
            }

            if (!checkThisExpressionsAreExplicatable(usages, expressionToProcess)) return

            if (data.isFirstParameter && expressionToProcess !is KtLambdaExpression) return

            usages += ConvertFunctionalReceiverLambdaUsage(expressionToProcess)
            return
        }

        if (data.isFirstParameter) return

        if (refElement is KtCallableReferenceExpression) {
            usages.add(
                ConflictUsageInfo(
                    refElement, KotlinBundle.message(
                        "callable.reference.transformation.is.not.supported.0", StringUtil.htmlEmphasize(refElement.text)
                    )
                )
            )
            return
        }
    }

    context(_: KaSession) private fun getArgumentExpressionToProcess(callElement: KtCallElement): KtExpression? {
        val argumentMapping = callElement.resolveToCall()?.successfulFunctionCallOrNull()?.argumentMapping ?: return null
        val parameter = data.changeInfo.method.getValueParameters()[data.functionParameterIndex]
        val entry = argumentMapping.entries.find { (_, value) -> value.name.asString() == parameter.name } ?: return null
        return KtPsiUtil.deparenthesize(entry.key)
    }

    context(_: KaSession) private fun checkThisExpressionsAreExplicatable(
        usages: MutableList<in UsageInfo>, expressionToProcess: KtExpression
    ): Boolean {
        for (thisExpr in expressionToProcess.collectDescendantsOfType<KtThisExpression>()) {
            if (thisExpr.getLabelName() != null) continue
            val resolved = thisExpr.instanceReference.mainReference.resolve() ?: continue
            if (resolved is KtNamedDeclaration && resolved.name == null) { // we can't qualify this
                usages.add(
                    ConflictUsageInfo(
                        thisExpr, KotlinBundle.message(
                            "following.expression.won.t.be.processed.since.refactoring.can.t.preserve.its.semantics.0", thisExpr.text
                        )
                    )
                )
                return false
            }
        }
        return true
    }

    private fun processInternalUsages(callable: KtFunction, usages: MutableList<UsageInfo>) {
        val body = when (callable) {
            is KtConstructor<*> -> callable.containingClassOrObject?.body
            else -> callable.bodyExpression
        }

        if (body != null) {
            val functionParameter = callable.valueParameters.getOrNull(data.functionParameterIndex) ?: return
            for (ref in ReferencesSearch.search(functionParameter, LocalSearchScope(body)).asIterable()) {
                val element = ref.element as? KtSimpleNameExpression ?: continue
                val callExpression = element.getParentOfTypeAndBranch<KtCallExpression> { calleeExpression }
                if (callExpression != null) {
                    usages += ConvertFunctionalReceiverParameterUsage(callExpression)
                } else if (!data.isFirstParameter) {
                    usages += ConvertFunctionalReceiverInternalUsage(element)
                }
            }
        }
    }

    private inner class ConvertFunctionalReceiverParameterUsage(element: KtCallExpression) : UsageInfo(element),
                                                                                             KotlinBaseChangeSignatureUsage {
        override fun processUsage(
            changeInfo: KotlinChangeInfoBase, element: KtElement, allUsages: Array<out UsageInfo>
        ): KtElement? {
            val callExpression = element as? KtCallExpression ?: return null
            val argumentList = callExpression.valueArgumentList ?: return null
            val expressionToMove = argumentList.arguments.getOrNull(data.typeParameterIndex)?.getArgumentExpression() ?: return null
            val callWithReceiver =
                KtPsiFactory(project).createExpressionByPattern("$0.$1", expressionToMove, callExpression) as KtQualifiedExpression
            (callWithReceiver.selectorExpression as KtCallExpression).valueArgumentList!!.removeArgument(data.typeParameterIndex)
            return callExpression.replace(callWithReceiver) as KtElement?
        }
    }

    private inner class ConvertFunctionalReceiverInternalUsage(element: KtSimpleNameExpression) : UsageInfo(element),
                                                                                                  KotlinBaseChangeSignatureUsage {
        override fun processUsage(
            changeInfo: KotlinChangeInfoBase, element: KtElement, allUsages: Array<out UsageInfo>
        ): KtElement? {
            val expression = element as? KtSimpleNameExpression ?: return null
            val parameterNames = suggestParameterNames(element, null)

            val receiver = parameterNames.getOrNull(data.typeParameterIndex) ?: return null
            val adapterLambda = KtPsiFactory(expression.project).createLambdaExpression(
                parameterNames.joinToString(), "$receiver.${expression.text}(${parameterNames.filter { it != receiver }.joinToString()})"
            )

            val replaced = expression.replaced(adapterLambda)
            allowAnalysisFromWriteActionInEdt(replaced) {
                replaced.moveFunctionLiteralOutsideParenthesesIfPossible()
            }
            return null
        }
    }

    private fun suggestParameterNames(element: KtExpression, initialText: String?): MutableList<String> {
        return allowAnalysisFromWriteActionInEdt(element) {
            val lambdaType = (data.changeInfo.method as KtFunction).valueParameters[data.functionParameterIndex].returnType
            val validator = UniqueNameGenerator()
            if (initialText != null) {
                validator.addExistingName(initialText)
            }
            val functionType = lambdaType as KaFunctionType

            fun suggestName(type: KaType): String =
                KotlinNameSuggester.suggestNamesByType(type, element, { p -> validator.value(p) }, "p").first()

            val parameterNames = functionType.parameterTypes.map(::suggestName).toMutableList()
            val receiverType = functionType.receiverType
            if (receiverType != null) { // the primary method is already processed, so the receiver is already extracted
                parameterNames.add(data.typeParameterIndex, suggestName(receiverType))
            }
            parameterNames
        }
    }

    private inner class ConvertFunctionalReceiverLambdaUsage(element: KtExpression) : UsageInfo(element), KotlinBaseChangeSignatureUsage {

        override fun processUsage(
            changeInfo: KotlinChangeInfoBase, element: KtElement, allUsages: Array<out UsageInfo>
        ): KtElement? {
            val expression = element as? KtExpression ?: return null
            val psiFactory = KtPsiFactory(expression.project)

            if (expression is KtLambdaExpression) {
                expression.valueParameters.getOrNull(data.typeParameterIndex)?.let { parameterToConvert ->
                    val thisRefExpr = psiFactory.createThisExpression()
                    val search = ReferencesSearch.search(parameterToConvert, LocalSearchScope(expression)).asIterable().toList()
                    for (ref in search) {
                        (ref.element as? KtSimpleNameExpression)?.replace(thisRefExpr)
                    }
                    val lambda = expression.functionLiteral
                    lambda.valueParameterList!!.removeParameter(parameterToConvert)
                    if (lambda.valueParameters.isEmpty()) {
                        lambda.arrow?.delete()
                    }
                }
                return null
            }

            val calleeText = when (expression) {
                is KtSimpleNameExpression -> expression.text
                is KtCallableReferenceExpression -> "(${expression.text})"
                else -> generateVariable(expression)
            }

            val parameterNames =
                suggestParameterNames(expression, if (expression !is KtCallableReferenceExpression) calleeText else null)

            val receiver = parameterNames.getOrNull(data.typeParameterIndex)
            val body = psiFactory.createExpression(
                parameterNames.joinToString(
                    prefix = "$calleeText(", postfix = ")"
                ) { if (it == receiver) "this" else it })
            parameterNames.removeAt(data.typeParameterIndex)
            val replacingLambda = psiFactory.buildExpression {
                appendFixedText("{ ")
                appendFixedText(parameterNames.joinToString())
                appendFixedText(" -> ")
                appendExpression(body)
                appendFixedText(" }")
            } as KtLambdaExpression

            val replaced = expression.replaced(replacingLambda)
            allowAnalysisFromWriteActionInEdt(replaced) {
                replaced.moveFunctionLiteralOutsideParenthesesIfPossible()
            }
            return null
        }

        private fun generateVariable(expression: KtExpression): String {
            var baseCallee = ""
            allowAnalysisFromWriteActionInEdt(expression) {
                K2IntroduceVariableHandler.collectCandidateTargetContainersAndDoRefactoring(
                    project, editor = null, expression, isVar = false, emptyList()
                ) {
                    baseCallee = it.name!!
                }
            }

            return baseCallee
        }
    }

    private class ConflictUsageInfo(element: PsiElement, @NlsContexts.DialogMessage val conflict: String) :
        UnresolvableCollisionUsageInfo(element, element) {
        override fun getDescription(): @NlsContexts.DialogMessage String = conflict
    }
}
