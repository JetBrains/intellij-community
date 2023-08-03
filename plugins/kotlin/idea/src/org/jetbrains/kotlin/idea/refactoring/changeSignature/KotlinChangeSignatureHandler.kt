// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor.Kind.SYNTHESIZED
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.intentions.isInvokeOperator
import org.jetbrains.kotlin.idea.util.expectedDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaCallableMemberDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.tasks.isDynamic
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class KotlinChangeSignatureHandler : KotlinChangeSignatureHandlerBase<DeclarationDescriptor>() {
    override fun asInvokeOperator(call: KtCallElement?): PsiElement? {
        val bindingContext = call?.analyze(BodyResolveMode.FULL) ?: return null
        if (call.getResolvedCall(bindingContext)?.resultingDescriptor?.isInvokeOperator == true) {
            return call
        }
        return null
    }

    override fun referencedClassOrCallable(calleeExpr: KtReferenceExpression): PsiElement? {
        val bindingContext = calleeExpr.parentOfType<KtElement>()?.analyze(BodyResolveMode.FULL) ?: return null
        val descriptor = bindingContext[BindingContext.REFERENCE_TARGET, calleeExpr]
        return if (descriptor is ClassDescriptor || descriptor is CallableDescriptor) calleeExpr else null
    }

    override fun isVarargFunction(function: DeclarationDescriptor): Boolean {
        return function is FunctionDescriptor && function.valueParameters.any { it.varargElementType != null }
    }

    override fun isSynthetic(function: DeclarationDescriptor, context: KtElement): Boolean {
        return function is FunctionDescriptor && function.kind === SYNTHESIZED
    }

    override fun isLibrary(function: DeclarationDescriptor, context: KtElement): Boolean {
        return function is DeserializedDescriptor
    }

    override fun isJavaCallable(function: DeclarationDescriptor): Boolean {
        return function is JavaCallableMemberDescriptor
    }

    override fun getDeclaration(t: DeclarationDescriptor, project: Project): PsiElement? {
        return DescriptorToSourceUtilsIde.getAnyDeclaration(project, t)
    }

    override fun isDynamic(function: DeclarationDescriptor): Boolean {
        return function.isDynamic()
    }

    override fun getDeclarationName(t: DeclarationDescriptor): String {
        return t.name.asString()
    }

    override fun runChangeSignature(project: Project,
                                    editor: Editor?,
                                    callableDescriptor: DeclarationDescriptor,
                                    context: PsiElement) {
        require(callableDescriptor is CallableDescriptor)
        runChangeSignature(project, editor, callableDescriptor, KotlinChangeSignatureConfiguration.Empty, context, null)
    }

    override fun findDescriptor(element: KtElement, project: Project, editor: Editor?): CallableDescriptor? {
        fun getDescriptor(element: KtElement): DeclarationDescriptor? {
            val bindingContext = element.analyze(BodyResolveMode.FULL)
            val descriptor = when {
                element is KtParameter && element.hasValOrVar() -> bindingContext[BindingContext.PRIMARY_CONSTRUCTOR_PARAMETER, element]
                element is KtReferenceExpression -> bindingContext[BindingContext.REFERENCE_TARGET, element]
                element is KtCallExpression -> element.getResolvedCall(bindingContext)?.resultingDescriptor
                else -> bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, element]
            }

            return if (descriptor is ClassDescriptor) descriptor.unsubstitutedPrimaryConstructor else descriptor
        }
        var descriptor = getDescriptor(element)
        if (descriptor is MemberDescriptor && descriptor.isActual) {
            descriptor = descriptor.expectedDescriptor() ?: descriptor
        }

        return when (descriptor) {
            is PropertyDescriptor -> descriptor
            is FunctionDescriptor -> descriptor
            else -> null
        }
    }
}
