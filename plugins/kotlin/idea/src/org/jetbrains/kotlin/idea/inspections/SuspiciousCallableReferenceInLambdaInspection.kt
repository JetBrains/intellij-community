// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.builtins.getReturnTypeFromFunctionType
import org.jetbrains.kotlin.builtins.isBuiltinFunctionalType
import org.jetbrains.kotlin.builtins.isFunctionOrSuspendFunctionType
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.intentions.ConvertLambdaToReferenceIntention
import org.jetbrains.kotlin.idea.intentions.getCallableDescriptor
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.resolveToDescriptors
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.calls.util.getParameterForArgument
import org.jetbrains.kotlin.resolve.calls.util.getParentCall
import org.jetbrains.kotlin.resolve.calls.util.getParentResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import org.jetbrains.kotlin.types.typeUtil.supertypes

class SuspiciousCallableReferenceInLambdaInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
        lambdaExpressionVisitor(fun(lambdaExpression) {
            val callableReference = lambdaExpression.bodyExpression?.statements?.singleOrNull() as? KtCallableReferenceExpression ?: return
            val context = lambdaExpression.analyze()
            val parentResolvedCall = lambdaExpression.getParentResolvedCall(context)
            if (parentResolvedCall != null) {
                val parameter = parentResolvedCall.getParameterForArgument(lambdaExpression.parent as? ValueArgument)
                val expectedType = parameter?.type
                if (expectedType?.isBuiltinFunctionalType == true) {
                    val returnType = expectedType.getReturnTypeFromFunctionType()
                    if (returnType.isFunctionOrSuspendFunctionType) return

                    val originalReturnType = parameter.original.type.getReturnTypeFromFunctionType()
                    if (originalReturnType.isFunctionInterfaceOrPropertyType() ||
                        originalReturnType.isTypeParameter() && originalReturnType.supertypes().any {
                            it.isFunctionInterfaceOrPropertyType()
                        }
                    ) return

                    if (parentResolvedCall.call.callElement.getParentCall(context) != null) return
                }
            }

            (parentResolvedCall?.call?.callElement as? KtExpression ?: lambdaExpression).let { expression ->
                if (expression.isUsedAsExpression(context)) {
                    val qualifiedOrThis = expression.getQualifiedExpressionForSelectorOrThis()
                    val parentDeclaration = qualifiedOrThis.getStrictParentOfType<KtDeclaration>()
                    val initializer = (parentDeclaration as? KtDeclarationWithInitializer)?.initializer
                    val typeReference = (parentDeclaration as? KtCallableDeclaration)?.typeReference
                    if (qualifiedOrThis != initializer || typeReference != null) return
                }
            }

            val quickFix = if (canMove(lambdaExpression, callableReference, context))
                arrayOf(IntentionWrapper(MoveIntoParenthesesIntention()))
            else
                LocalQuickFix.EMPTY_ARRAY

            holder.registerProblem(
                lambdaExpression,
                KotlinBundle.message("suspicious.callable.reference.as.the.only.lambda.element"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                *quickFix
            )
        })

    private fun canMove(
        lambdaExpression: KtLambdaExpression,
        callableReference: KtCallableReferenceExpression,
        context: BindingContext
    ): Boolean {
        val lambdaDescriptor = context[BindingContext.FUNCTION, lambdaExpression.functionLiteral] ?: return false
        val lambdaParameter = lambdaDescriptor.extensionReceiverParameter ?: lambdaDescriptor.valueParameters.singleOrNull()

        val functionReceiver = callableReference.receiverExpression?.mainReference?.resolveToDescriptors(context)?.firstOrNull()
        if (functionReceiver == lambdaParameter) return true
        val lambdaParameterType = lambdaParameter?.type
        if (functionReceiver is VariableDescriptor && functionReceiver.type == lambdaParameterType) return true
        if (functionReceiver is ClassDescriptor && functionReceiver == lambdaParameterType?.constructor?.declarationDescriptor) return true

        if (lambdaParameterType == null) return false
        val functionDescriptor =
            callableReference.callableReference.mainReference.resolveToDescriptors(context).firstOrNull() as? FunctionDescriptor
        val functionParameterType = functionDescriptor?.valueParameters?.firstOrNull()?.type ?: return false
        return functionParameterType == lambdaParameterType
    }

    internal class MoveIntoParenthesesIntention : ConvertLambdaToReferenceIntention(
        KotlinBundle.messagePointer("move.reference.into.parentheses")
    ) {
        override fun buildReferenceText(lambdaExpression: KtLambdaExpression): String? {
            val callableReferenceExpression =
                lambdaExpression.bodyExpression?.statements?.singleOrNull() as? KtCallableReferenceExpression ?: return null
            val callableReference = callableReferenceExpression.callableReference
            val receiverExpression = callableReferenceExpression.receiverExpression
            val receiver = if (receiverExpression == null) {
                ""
            } else {
                val descriptor = receiverExpression.getCallableDescriptor()
                val literal = lambdaExpression.functionLiteral
                if (descriptor == null ||
                    descriptor is ValueParameterDescriptor && descriptor.containingDeclaration == literal.resolveToDescriptorIfAny()
                ) {
                    callableReference.resolveToCall(BodyResolveMode.FULL)
                        ?.let { it.extensionReceiver ?: it.dispatchReceiver }
                        ?.let { IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.renderType(it.type) } ?: ""
                } else {
                    receiverExpression.text
                }
            }

            return "$receiver::${callableReference.text}"
        }

        override fun isApplicableTo(element: KtLambdaExpression) = true

        override fun skipProcessingFurtherElementsAfter(element: PsiElement): Boolean = false
    }
}

private val functionInterfaces: Set<FqName> = listOf(
    "kotlin.Function",
    "kotlin.reflect.KFunction"
).map { FqName(it) }.toSet()

private val propertyTypes: Set<FqName> = listOf(
    "kotlin.reflect.KProperty",
    "kotlin.reflect.KProperty0",
    "kotlin.reflect.KProperty1",
    "kotlin.reflect.KMutableProperty",
    "kotlin.reflect.KMutableProperty0",
    "kotlin.reflect.KMutableProperty1",
).map { FqName(it) }.toSet()

private fun KotlinType.isFunctionInterfaceOrPropertyType(): Boolean =
    fqName in functionInterfaces || fqName in propertyTypes