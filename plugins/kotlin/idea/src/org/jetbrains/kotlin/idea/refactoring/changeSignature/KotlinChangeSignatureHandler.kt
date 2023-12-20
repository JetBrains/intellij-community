// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.changeSignature.ChangeSignatureUtil
import com.intellij.refactoring.util.CommonRefactoringUtil
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

class KotlinChangeSignatureHandler : KotlinChangeSignatureHandlerBase() {
    override fun asInvokeOperator(call: KtCallElement?): PsiElement? {
        val bindingContext = call?.analyze(BodyResolveMode.FULL) ?: return null
        if (call.getResolvedCall(bindingContext)?.resultingDescriptor?.isInvokeOperator == true) {
            return call
        }
        return null
    }

    override fun invokeChangeSignature(
        element: KtElement,
        context: PsiElement,
        project: Project,
        editor: Editor?,
        dataContext: DataContext?
    ){
        val callableDescriptor = findDescriptor(element)
        if (!checkDescriptor(callableDescriptor, project, editor)) return

        require(callableDescriptor is CallableDescriptor)
        runChangeSignature(project, editor, callableDescriptor, KotlinChangeSignatureConfiguration.Empty, context, null)
    }

    fun findDescriptor(element: KtElement): CallableDescriptor? {
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

    fun checkDescriptor(callableDescriptor: DeclarationDescriptor?, project: Project, editor: Editor?): Boolean {
        val elementKind = when {
            callableDescriptor == null -> InapplicabilityKind.Null
            callableDescriptor is FunctionDescriptor && callableDescriptor.valueParameters.any { it.varargElementType != null } -> InapplicabilityKind.Varargs
            callableDescriptor is FunctionDescriptor && callableDescriptor.kind === SYNTHESIZED -> InapplicabilityKind.Synthetic
            callableDescriptor is DeserializedDescriptor -> InapplicabilityKind.Library
            callableDescriptor is JavaCallableMemberDescriptor -> InapplicabilityKind.JavaCallable
            callableDescriptor.isDynamic() -> InapplicabilityKind.Dynamic
            else -> null
        } ?: return true

        val message = RefactoringBundle.getCannotRefactorMessage(
            elementKind.description
        )
        if (elementKind == InapplicabilityKind.JavaCallable) {
            val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(project, callableDescriptor!!)
            if (declaration is PsiClass) {
                val message = RefactoringBundle.getCannotRefactorMessage(
                  RefactoringBundle.message("error.wrong.caret.position.method.or.class.name")
                )
                CommonRefactoringUtil.showErrorHint(
                  project,
                  editor,
                  message,
                  RefactoringBundle.message("changeSignature.refactoring.name"),
                  "refactoring.changeSignature",
                )
                return false
            }
            assert(declaration is PsiMethod) { "PsiMethod expected: $callableDescriptor" }
            ChangeSignatureUtil.invokeChangeSignatureOn(declaration as PsiMethod, project)
            return false
        }

        CommonRefactoringUtil.showErrorHint(
            project,
            editor,
            message,
            RefactoringBundle.message("changeSignature.refactoring.name"),
            HelpID.CHANGE_SIGNATURE
        )
        return false
    }

}
