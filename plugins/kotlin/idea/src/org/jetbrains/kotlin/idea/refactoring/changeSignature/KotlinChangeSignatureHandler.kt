// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler
import com.intellij.refactoring.changeSignature.ChangeSignatureUtil
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor.Kind.SYNTHESIZED
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.surroundWith.KotlinSurrounderUtils
import org.jetbrains.kotlin.idea.intentions.isInvokeOperator
import org.jetbrains.kotlin.idea.util.expectedDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaCallableMemberDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.tasks.isDynamic
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

class KotlinChangeSignatureHandler : ChangeSignatureHandler {
    override fun findTargetMember(element: PsiElement) = findTargetForRefactoring(element)

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)

        val element = findTargetMember(file, editor) ?: CommonDataKeys.PSI_ELEMENT.getData(dataContext) ?: return
        val elementAtCaret = file.findElementAt(editor.caretModel.offset) ?: return
        if (element !is KtElement)
            throw KotlinExceptionWithAttachments("This handler must be invoked for Kotlin elements only: ${element::class.java}")
                .withAttachment("element", element)

        invokeChangeSignature(element, elementAtCaret, project, editor)
    }

    override fun invoke(project: Project, elements: Array<PsiElement>, dataContext: DataContext?) {
        val element = elements.singleOrNull()?.unwrapped ?: return
        if (element !is KtElement) {
            throw KotlinExceptionWithAttachments("This handler must be invoked for Kotlin elements only: ${element::class.java}")
                .withAttachment("element", element)
        }

        val editor = dataContext?.let { CommonDataKeys.EDITOR.getData(it) }
        invokeChangeSignature(element, element, project, editor)
    }

    override fun getTargetNotFoundMessage() = KotlinBundle.message("error.wrong.caret.position.function.or.constructor.name")

    companion object {
        fun findTargetForRefactoring(element: PsiElement): PsiElement? {
            val elementParent = element.parent
            if ((elementParent is KtNamedFunction || elementParent is KtClass || elementParent is KtProperty) &&
                (elementParent as KtNamedDeclaration).nameIdentifier === element
            ) return elementParent

            if (elementParent is KtParameter &&
                elementParent.hasValOrVar() &&
                elementParent.parentOfType<KtPrimaryConstructor>()?.valueParameterList === elementParent.parent
            ) return elementParent

            if (elementParent is KtProperty && elementParent.valOrVarKeyword === element) return elementParent
            if (elementParent is KtConstructor<*> && elementParent.getConstructorKeyword() === element) return elementParent

            element.parentOfType<KtParameterList>()?.let { parameterList ->
                return PsiTreeUtil.getParentOfType(parameterList, KtFunction::class.java, KtProperty::class.java, KtClass::class.java)
            }

            element.parentOfType<KtTypeParameterList>()?.let { typeParameterList ->
                return PsiTreeUtil.getParentOfType(typeParameterList, KtFunction::class.java, KtProperty::class.java, KtClass::class.java)
            }

            val call: KtCallElement? = PsiTreeUtil.getParentOfType(
                element,
                KtCallExpression::class.java,
                KtSuperTypeCallEntry::class.java,
                KtConstructorDelegationCall::class.java
            )

            val calleeExpr = call?.let {
                val callee = it.calleeExpression
                (callee as? KtConstructorCalleeExpression)?.constructorReferenceExpression ?: callee
            } ?: element.parentOfType<KtSimpleNameExpression>()

            if (calleeExpr is KtSimpleNameExpression || calleeExpr is KtConstructorDelegationReferenceExpression) {
                val bindingContext = element.parentOfType<KtElement>()?.analyze(BodyResolveMode.FULL) ?: return null

                if (call?.getResolvedCall(bindingContext)?.resultingDescriptor?.isInvokeOperator == true) return call

                val descriptor = bindingContext[BindingContext.REFERENCE_TARGET, calleeExpr as KtReferenceExpression]
                if (descriptor is ClassDescriptor || descriptor is CallableDescriptor) return calleeExpr
            }

            return null
        }

        fun invokeChangeSignature(element: KtElement, context: PsiElement, project: Project, editor: Editor?) {
            val bindingContext = element.analyze(BodyResolveMode.FULL)

            val callableDescriptor = findDescriptor(element, project, editor, bindingContext) ?: return
            if (callableDescriptor is DeserializedDescriptor) {
                return CommonRefactoringUtil.showErrorHint(
                    project,
                    editor,
                    KotlinBundle.message("error.hint.library.declarations.cannot.be.changed"),
                    RefactoringBundle.message("changeSignature.refactoring.name"),
                    "refactoring.changeSignature",
                )
            }

            if (callableDescriptor is JavaCallableMemberDescriptor) {
                val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(project, callableDescriptor)
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
                    return
                }
                assert(declaration is PsiMethod) { "PsiMethod expected: $callableDescriptor" }
                ChangeSignatureUtil.invokeChangeSignatureOn(declaration as PsiMethod, project)
                return
            }

            if (callableDescriptor.isDynamic()) {
                if (editor != null) {
                    KotlinSurrounderUtils.showErrorHint(
                        project,
                        editor,
                        KotlinBundle.message("message.change.signature.is.not.applicable.to.dynamically.invoked.functions"),
                        RefactoringBundle.message("changeSignature.refactoring.name"),
                        null
                    )
                }
                return
            }

            runChangeSignature(project, editor, callableDescriptor, KotlinChangeSignatureConfiguration.Empty, context, null)
        }

        private fun getDescriptor(bindingContext: BindingContext, element: PsiElement): DeclarationDescriptor? {
            val descriptor = when {
                element is KtParameter && element.hasValOrVar() -> bindingContext[BindingContext.PRIMARY_CONSTRUCTOR_PARAMETER, element]
                element is KtReferenceExpression -> bindingContext[BindingContext.REFERENCE_TARGET, element]
                element is KtCallExpression -> element.getResolvedCall(bindingContext)?.resultingDescriptor
                else -> bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, element]
            }

            return if (descriptor is ClassDescriptor) descriptor.unsubstitutedPrimaryConstructor else descriptor
        }

        fun findDescriptor(element: PsiElement, project: Project, editor: Editor?, bindingContext: BindingContext): CallableDescriptor? {
            if (!CommonRefactoringUtil.checkReadOnlyStatus(project, element)) return null

            var descriptor = getDescriptor(bindingContext, element)
            if (descriptor is MemberDescriptor && descriptor.isActual) {
                descriptor = descriptor.expectedDescriptor() ?: descriptor
            }

            return when (descriptor) {
                is PropertyDescriptor -> descriptor
                is FunctionDescriptor -> {
                    if (descriptor.valueParameters.any { it.varargElementType != null }) {
                        val message = KotlinBundle.message("error.cant.refactor.vararg.functions")
                        CommonRefactoringUtil.showErrorHint(
                            project,
                            editor,
                            message,
                            RefactoringBundle.message("changeSignature.refactoring.name"),
                            HelpID.CHANGE_SIGNATURE
                        )
                        return null
                    }

                    if (descriptor.kind === SYNTHESIZED) {
                        val message = KotlinBundle.message("cannot.refactor.synthesized.function", descriptor.name)
                        CommonRefactoringUtil.showErrorHint(
                            project,
                            editor,
                            message,
                            RefactoringBundle.message("changeSignature.refactoring.name"),
                            HelpID.CHANGE_SIGNATURE
                        )
                        return null
                    }

                    descriptor
                }

                else -> {
                    val message = RefactoringBundle.getCannotRefactorMessage(
                        KotlinBundle.message("error.wrong.caret.position.function.or.constructor.name")
                    )

                    CommonRefactoringUtil.showErrorHint(
                        project,
                        editor,
                        message,
                        RefactoringBundle.message("changeSignature.refactoring.name"),
                        HelpID.CHANGE_SIGNATURE
                    )
                    null
                }
            }
        }
    }
}
