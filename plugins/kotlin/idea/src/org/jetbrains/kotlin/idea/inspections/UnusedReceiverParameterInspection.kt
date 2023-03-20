// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ReceiverParameterDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptorWithAccessors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeWithContentNonSourceRootCode
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.core.isOverridable
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.idea.intentions.receiverType
import org.jetbrains.kotlin.idea.isMainFunction
import org.jetbrains.kotlin.idea.quickfix.RemoveUnusedFunctionParameterFix
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinChangeSignatureConfiguration
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.refactoring.changeSignature.modify
import org.jetbrains.kotlin.idea.refactoring.changeSignature.runChangeSignature
import org.jetbrains.kotlin.idea.refactoring.explicateAsTextForReceiver
import org.jetbrains.kotlin.idea.refactoring.getThisLabelName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.getThisReceiverOwner
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.typeRefHelpers.setReceiverTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class UnusedReceiverParameterInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            private fun check(callableDeclaration: KtCallableDeclaration) {
                val receiverTypeReference = callableDeclaration.receiverTypeReference
                if (receiverTypeReference == null || receiverTypeReference.textRange.isEmpty) return

                if (callableDeclaration is KtProperty && callableDeclaration.accessors.isEmpty()) return
                if (callableDeclaration is KtNamedFunction) {
                    if (!callableDeclaration.hasBody()) return
                    if (callableDeclaration.name == null) {
                        val parentQualified = callableDeclaration.getStrictParentOfType<KtQualifiedExpression>()
                        if (KtPsiUtil.deparenthesize(parentQualified?.callExpression?.calleeExpression) == callableDeclaration) return
                    }
                }

                if (callableDeclaration.hasModifier(KtTokens.OVERRIDE_KEYWORD) ||
                    callableDeclaration.hasModifier(KtTokens.OPERATOR_KEYWORD) ||
                    callableDeclaration.hasModifier(KtTokens.INFIX_KEYWORD) ||
                    callableDeclaration.hasActualModifier() ||
                    callableDeclaration.isOverridable()
                ) return

                val context = callableDeclaration.safeAnalyzeWithContentNonSourceRootCode()
                if (context == BindingContext.EMPTY) return
                val receiverType = context[BindingContext.TYPE, receiverTypeReference] ?: return
                val receiverTypeDeclaration = receiverType.constructor.declarationDescriptor
                if (DescriptorUtils.isCompanionObject(receiverTypeDeclaration)) return

                val callable = context[BindingContext.DECLARATION_TO_DESCRIPTOR, callableDeclaration] ?: return

                if (callableDeclaration.isMainFunction(callable)) return

                val containingDeclaration = callable.containingDeclaration
                if (containingDeclaration != null && containingDeclaration == receiverTypeDeclaration) {
                    val thisLabelName = containingDeclaration.getThisLabelName()
                    val thisLabelNamesInCallable =
                        callableDeclaration.collectDescendantsOfType<KtThisExpression>().mapNotNull { it.getLabelName() }
                    if (thisLabelNamesInCallable.isNotEmpty()) {
                        if (thisLabelNamesInCallable.none { it == thisLabelName }) {
                            registerProblem(receiverTypeReference, true)
                        }
                        return
                    }
                }

                var used = false
                callableDeclaration.acceptChildren(object : KtVisitorVoid() {
                    override fun visitKtElement(element: KtElement) {
                        if (used) return
                        element.acceptChildren(this)

                        if (isUsageOfDescriptor(callable, element, context)) {
                            used = true
                        }
                    }
                })

                if (!used) registerProblem(receiverTypeReference)
            }

            override fun visitNamedFunction(function: KtNamedFunction) {
                check(function)
            }

            override fun visitProperty(property: KtProperty) {
                check(property)
            }

            private fun registerProblem(receiverTypeReference: KtTypeReference, inSameClass: Boolean = false) {
                holder.registerProblem(
                    receiverTypeReference,
                    KotlinBundle.message("inspection.unused.receiver.parameter"),
                    RemoveReceiverFix(inSameClass)
                )
            }
        }
    }

    class RemoveReceiverFix(private val inSameClass: Boolean) : LocalQuickFix {
        override fun getName(): String = actionName

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as? KtTypeReference ?: return
            if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return

            apply(element, project, inSameClass)
        }

        override fun getFamilyName(): String = actionName

        override fun startInWriteAction() = false

        companion object {
            @Nls
            private val actionName = KotlinBundle.message("fix.unused.receiver.parameter.remove")

            private fun configureChangeSignature() = object : KotlinChangeSignatureConfiguration {
                override fun performSilently(affectedFunctions: Collection<PsiElement>) = true
                override fun configure(originalDescriptor: KotlinMethodDescriptor) = originalDescriptor.modify { it.removeParameter(0) }
            }

            fun apply(element: KtTypeReference, project: Project, inSameClass: Boolean = false) {
                val function = element.parent as? KtCallableDeclaration ?: return
                val callableDescriptor = function.resolveToDescriptorIfAny(BodyResolveMode.FULL) as? CallableDescriptor ?: return

                val typeParameters = RemoveUnusedFunctionParameterFix.typeParameters(element)
                if (inSameClass) {
                    runWriteAction {
                        val explicateAsTextForReceiver = callableDescriptor.explicateAsTextForReceiver()
                        function.forEachDescendantOfType<KtThisExpression> {
                            if (it.text == explicateAsTextForReceiver) it.labelQualifier?.delete()
                        }
                        function.setReceiverTypeReference(null)
                    }
                } else {
                    runChangeSignature(project, function.findExistingEditor(), callableDescriptor, configureChangeSignature(), element, actionName)
                }
                RemoveUnusedFunctionParameterFix.runRemoveUnusedTypeParameters(typeParameters)
            }
        }
    }
}

fun isUsageOfDescriptor(descriptor: DeclarationDescriptor, element: KtElement, context: BindingContext): Boolean {
    fun isUsageOfDescriptorInResolvedCall(resolvedCall: ResolvedCall<*>): Boolean {
        // As receiver of call
        if (resolvedCall.dispatchReceiver.getThisReceiverOwner(context) == descriptor ||
            resolvedCall.extensionReceiver.getThisReceiverOwner(context) == descriptor ||
            resolvedCall.contextReceivers.any { it.getThisReceiverOwner(context) == descriptor }
        ) {
            return true
        }
        // As explicit "this"
        if ((resolvedCall.candidateDescriptor as? ReceiverParameterDescriptor)?.containingDeclaration == descriptor) {
            return true
        }

        if (resolvedCall is VariableAsFunctionResolvedCall) {
            return isUsageOfDescriptorInResolvedCall(resolvedCall.variableCall)
        }

        return false
    }

    if (element !is KtExpression) return false

    if (element is KtClassLiteralExpression) {
        val typeParameter = element.receiverExpression?.mainReference?.resolve() as? KtTypeParameter
        val typeParameterDescriptor = context[BindingContext.TYPE_PARAMETER, typeParameter]
        if (descriptor.safeAs<CallableDescriptor>()?.receiverType()?.constructor == typeParameterDescriptor?.typeConstructor) return true
    }

    return when (element) {
        is KtDestructuringDeclarationEntry -> {
            listOf { context[BindingContext.COMPONENT_RESOLVED_CALL, element] }
        }
        is KtProperty -> {
            val elementDescriptor = context[BindingContext.DECLARATION_TO_DESCRIPTOR, element] as? VariableDescriptorWithAccessors
            if (elementDescriptor != null) {
                listOf(
                    { context[BindingContext.DELEGATED_PROPERTY_RESOLVED_CALL, elementDescriptor.getter] },
                    { context[BindingContext.DELEGATED_PROPERTY_RESOLVED_CALL, elementDescriptor.setter] },
                    { context[BindingContext.PROVIDE_DELEGATE_RESOLVED_CALL, elementDescriptor] },
                )
            } else {
                emptyList()
            }
        }
        else -> {
            listOf(
                { element.getResolvedCall(context) },
                { context[BindingContext.LOOP_RANGE_ITERATOR_RESOLVED_CALL, element] },
                { context[BindingContext.LOOP_RANGE_HAS_NEXT_RESOLVED_CALL, element] },
                { context[BindingContext.LOOP_RANGE_NEXT_RESOLVED_CALL, element] }
            )
        }
    }.any { getResolveCall ->
        val resolvedCall = getResolveCall() ?: return@any false
        isUsageOfDescriptorInResolvedCall(resolvedCall)
    }
}
