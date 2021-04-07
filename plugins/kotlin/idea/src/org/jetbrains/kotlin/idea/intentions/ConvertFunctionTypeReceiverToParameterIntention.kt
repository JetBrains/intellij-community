// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.builtins.getReceiverTypeFromFunctionType
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.core.*
import org.jetbrains.kotlin.idea.core.util.runSynchronouslyWithProgress
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.refactoring.CallableRefactoring
import org.jetbrains.kotlin.idea.refactoring.checkConflictsInteractively
import org.jetbrains.kotlin.idea.refactoring.getAffectedCallables
import org.jetbrains.kotlin.idea.references.KtSimpleReference
import org.jetbrains.kotlin.idea.search.usagesSearch.searchReferencesOrMethodReferences
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.idea.util.getReceiverTargetDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.psi.typeRefHelpers.setReceiverTypeReference
import org.jetbrains.kotlin.psi2ir.deparenthesize
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.bindingContextUtil.getTargetFunctionDescriptor
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.resolvedCallUtil.getArgumentByParameterIndex
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ConvertFunctionTypeReceiverToParameterIntention : SelfTargetingRangeIntention<KtTypeReference>(
    KtTypeReference::class.java,
    KotlinBundle.lazyMessage("convert.function.type.receiver.to.parameter")
) {
    class ConversionData(
        val functionParameterIndex: Int?,
        val lambdaReceiverType: KotlinType,
        callableDeclaration: KtCallableDeclaration,
    ) {
        private val declarationPointer = callableDeclaration.createSmartPointer()
        val callableDeclaration: KtCallableDeclaration? get() = declarationPointer.element
        val functionDescriptor by lazy { callableDeclaration.unsafeResolveToDescriptor() as CallableDescriptor }

        fun functionType(declaration: KtCallableDeclaration? = callableDeclaration): KtFunctionType? {
            val functionTypeOwner = if (functionParameterIndex != null)
                declaration?.valueParameters?.getOrNull(functionParameterIndex)
            else
                declaration

            return functionTypeOwner?.typeReference?.typeElement as? KtFunctionType
        }
    }

    class CallableDefinitionInfo(element: KtCallableDeclaration) :
        AbstractProcessableUsageInfo<KtCallableDeclaration, ConversionData>(element) {
        override fun process(data: ConversionData, elementsToShorten: MutableList<KtElement>) {
            val declaration = element ?: return
            val functionType = data.functionType(declaration) ?: return
            val functionTypeParameterList = functionType.parameterList ?: return
            val functionTypeReceiver = functionType.receiverTypeReference ?: return
            val parameterToAdd = KtPsiFactory(project).createFunctionTypeParameter(functionTypeReceiver)
            functionTypeParameterList.addParameterBefore(parameterToAdd, functionTypeParameterList.parameters.firstOrNull())
            functionType.setReceiverTypeReference(null)
        }
    }

    class ParameterCallInfo(element: KtCallExpression) : AbstractProcessableUsageInfo<KtCallExpression, ConversionData>(element) {
        override fun process(data: ConversionData, elementsToShorten: MutableList<KtElement>) {
            val callExpression = element ?: return
            val qualifiedExpression = callExpression.getQualifiedExpressionForSelector() ?: return
            val receiverExpression = qualifiedExpression.receiverExpression
            val argumentList = callExpression.getOrCreateValueArgumentList()
            argumentList.addArgumentBefore(KtPsiFactory(project).createArgument(receiverExpression), argumentList.arguments.firstOrNull())
            qualifiedExpression.replace(callExpression)
        }
    }

    class LambdaInfo(element: KtLambdaExpression) : AbstractProcessableUsageInfo<KtLambdaExpression, ConversionData>(element) {
        override fun process(data: ConversionData, elementsToShorten: MutableList<KtElement>) {
            val lambda = element?.functionLiteral ?: return
            val context = lambda.analyze()
            val lambdaDescriptor = context[BindingContext.FUNCTION, lambda] ?: return

            val psiFactory = KtPsiFactory(project)
            val validator = CollectingNameValidator(
                lambda.valueParameters.mapNotNull { it.name },
                NewDeclarationNameValidator(lambda.bodyExpression!!, null, NewDeclarationNameValidator.Target.VARIABLES)
            )

            val lambdaExtensionReceiver = lambdaDescriptor.extensionReceiverParameter
            val lambdaDispatchReceiver = lambdaDescriptor.dispatchReceiverParameter

            val newParameterName = KotlinNameSuggester.suggestNamesByType(data.lambdaReceiverType, validator, "p").first()
            val newParameterRefExpression = psiFactory.createExpression(newParameterName)

            lambda.accept(object : KtTreeVisitorVoid() {
                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                    super.visitSimpleNameExpression(expression)
                    if (expression is KtOperationReferenceExpression) return
                    val resolvedCall = expression.getResolvedCall(context) ?: return
                    val dispatchReceiverTarget = resolvedCall.dispatchReceiver?.getReceiverTargetDescriptor(context)
                    val extensionReceiverTarget = resolvedCall.extensionReceiver?.getReceiverTargetDescriptor(context)
                    if (dispatchReceiverTarget == lambdaDescriptor || extensionReceiverTarget == lambdaDescriptor) {
                        val parent = expression.parent
                        if (parent is KtCallExpression && expression == parent.calleeExpression) {
                            if ((parent.parent as? KtQualifiedExpression)?.receiverExpression !is KtThisExpression) {
                                parent.replace(psiFactory.createExpressionByPattern("$0.$1", newParameterName, parent))
                            }
                        } else if (parent is KtQualifiedExpression && parent.receiverExpression is KtThisExpression) {
                            // do nothing
                        } else {
                            val referencedName = expression.getReferencedName()
                            expression.replace(psiFactory.createExpressionByPattern("$newParameterName.$referencedName"))
                        }
                    }
                }

                override fun visitThisExpression(expression: KtThisExpression) {
                    val resolvedCall = expression.getResolvedCall(context) ?: return
                    if (resolvedCall.resultingDescriptor == lambdaDispatchReceiver ||
                        resolvedCall.resultingDescriptor == lambdaExtensionReceiver
                    ) {
                        expression.replace(newParameterRefExpression.copy())
                    }
                }
            })

            val lambdaParameterList = lambda.getOrCreateParameterList()
            if (lambda.valueParameters.isEmpty() && lambdaDescriptor.valueParameters.isNotEmpty()) {
                val parameterToAdd = psiFactory.createLambdaParameterList("it").parameters.first()
                lambdaParameterList.addParameterBefore(parameterToAdd, lambdaParameterList.parameters.firstOrNull())
            }

            val parameterToAdd = psiFactory.createLambdaParameterList(newParameterName).parameters.first()
            lambdaParameterList.addParameterBefore(parameterToAdd, lambdaParameterList.parameters.firstOrNull())
        }
    }

    private inner class Converter(
        private val data: ConversionData,
        editor: Editor?,
        project: Project,
    ) : CallableRefactoring<CallableDescriptor>(project, editor, data.functionDescriptor, text) {
        override fun performRefactoring(descriptorsForChange: Collection<CallableDescriptor>) {
            val callables = getAffectedCallables(project, descriptorsForChange)

            val conflicts = MultiMap<PsiElement, String>()

            val usages = ArrayList<AbstractProcessableUsageInfo<*, ConversionData>>()

            project.runSynchronouslyWithProgress(KotlinBundle.message("looking.for.usages.and.conflicts"), true) {
                runReadAction {
                    val progressStep = 1.0 / callables.size
                    val progressIndicator = ProgressManager.getInstance().progressIndicator
                    progressIndicator.isIndeterminate = false

                    for ((i, callable) in callables.withIndex()) {
                        progressIndicator.fraction = (i + 1) * progressStep

                        if (callable !is KtCallableDeclaration) continue

                        if (!checkModifiable(callable)) {
                            val renderedCallable = RefactoringUIUtil.getDescription(callable, true).replaceFirstChar(Char::uppercaseChar)
                            conflicts.putValue(callable, KotlinBundle.message("can.t.modify.0", renderedCallable))
                        }

                        for (ref in callable.searchReferencesOrMethodReferences()) {
                            if (ref !is KtSimpleReference<*>) continue
                            processExternalUsage(ref, usages)
                        }

                        usages += CallableDefinitionInfo(callable)

                        processInternalUsages(callable, usages)
                    }
                }
            }

            project.checkConflictsInteractively(conflicts) {
                project.executeWriteCommand(text) {
                    val elementsToShorten = ArrayList<KtElement>()
                    usages.sortedByDescending { it.element?.textOffset }.forEach { it.process(data, elementsToShorten) }
                    ShortenReferences.DEFAULT.process(elementsToShorten)
                }
            }
        }

        private fun processExternalUsage(
            ref: KtSimpleReference<*>,
            usages: ArrayList<AbstractProcessableUsageInfo<*, ConversionData>>
        ) {
            val parameterIndex = data.functionParameterIndex ?: return
            val callElement = ref.element.getParentOfTypeAndBranch<KtCallElement> { calleeExpression } ?: return
            val context = callElement.analyze(BodyResolveMode.PARTIAL)
            val expressionToProcess = callElement
                .getArgumentByParameterIndex(parameterIndex, context)
                .singleOrNull()
                ?.getArgumentExpression()
                ?.let { KtPsiUtil.safeDeparenthesize(it) }
                ?: return

            if (expressionToProcess is KtLambdaExpression) {
                usages += LambdaInfo(expressionToProcess)
            }
        }

        private fun processInternalUsages(
            callable: KtCallableDeclaration,
            usages: ArrayList<AbstractProcessableUsageInfo<*, ConversionData>>
        ) {
            val parameterIndex = data.functionParameterIndex ?: return processLambdasInReturnExpressions(callable, usages)
            val body = when (callable) {
                is KtConstructor<*> -> callable.containingClassOrObject?.body
                is KtDeclarationWithBody -> callable.bodyExpression
                else -> null
            }

            if (body != null) {
                val functionParameter = callable.valueParameters.getOrNull(parameterIndex) ?: return
                for (ref in ReferencesSearch.search(functionParameter, LocalSearchScope(body))) {
                    val element = ref.element as? KtSimpleNameExpression ?: continue
                    val callExpression = element.getParentOfTypeAndBranch<KtCallExpression> { calleeExpression } ?: continue
                    usages += ParameterCallInfo(callExpression)
                }
            }
        }

        private fun processLambdasInReturnExpressions(
            callable: KtCallableDeclaration,
            usages: ArrayList<AbstractProcessableUsageInfo<*, ConversionData>>,
        ) {
            assert(data.functionParameterIndex == null)

            when (callable) {
                is KtNamedFunction -> processBody(callable, usages, callable.bodyExpression)
                is KtProperty -> {
                    processBody(callable, usages, callable.initializer)
                    callable.getter?.let { processBody(it, usages, it.bodyExpression) }
                }

                else -> Unit
            }
        }

        private fun processBody(
            declaration: KtDeclaration,
            usages: ArrayList<AbstractProcessableUsageInfo<*, ConversionData>>,
            bodyExpression: KtExpression?,
        ) = when (val body = bodyExpression?.deparenthesize()) {
            is KtLambdaExpression -> usages += LambdaInfo(body)
            is KtBlockExpression -> {
                val context by lazy { declaration.analyze(BodyResolveMode.PARTIAL_WITH_CFA) }
                val target by lazy { context[BindingContext.DECLARATION_TO_DESCRIPTOR, declaration] }
                bodyExpression.forEachDescendantOfType<KtReturnExpression> { returnExpression ->
                    returnExpression.returnedExpression
                        ?.deparenthesize()
                        ?.safeAs<KtLambdaExpression>()
                        ?.takeIf { returnExpression.getTargetFunctionDescriptor(context) == target }
                        ?.let { usages += LambdaInfo(it) }
                }
            }

            else -> Unit
        }
    }

    private fun KtTypeReference.getConversionData(): ConversionData? {
        val functionTypeReceiver = parent as? KtFunctionTypeReceiver ?: return null
        val functionType = functionTypeReceiver.parent as? KtFunctionType ?: return null
        val lambdaReceiverType = functionType
            .getAbbreviatedTypeOrType(functionType.analyze(BodyResolveMode.PARTIAL))
            ?.getReceiverTypeFromFunctionType()
            ?: return null

        val typeReferenceHolder = functionType.parent?.safeAs<KtTypeReference>()?.parent?.safeAs<KtCallableDeclaration>() ?: return null
        val (callableDeclaration, parameterIndex) = if (typeReferenceHolder is KtParameter) {
            val callableDeclaration = typeReferenceHolder.ownerFunction as? KtCallableDeclaration ?: return null
            callableDeclaration to callableDeclaration.valueParameters.indexOf(typeReferenceHolder)
        } else
            typeReferenceHolder to null

        return ConversionData(parameterIndex, lambdaReceiverType, callableDeclaration)
    }

    override fun startInWriteAction(): Boolean = false

    override fun applicabilityRange(element: KtTypeReference): TextRange? {
        val data = element.getConversionData() ?: return null

        val elementBefore = data.functionType() ?: return null
        val elementAfter = elementBefore.copied().apply {
            parameterList?.addParameterBefore(
                KtPsiFactory(element).createFunctionTypeParameter(element),
                parameterList?.parameters?.firstOrNull()
            )
            setReceiverTypeReference(null)
        }

        setTextGetter(KotlinBundle.lazyMessage("convert.0.to.1", elementBefore.text, elementAfter.text))
        return element.textRange
    }

    override fun applyTo(element: KtTypeReference, editor: Editor?) {
        element.getConversionData()?.let { Converter(it, editor, element.project).run() }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction = ConvertFunctionTypeReceiverToParameterIntention()
    }
}