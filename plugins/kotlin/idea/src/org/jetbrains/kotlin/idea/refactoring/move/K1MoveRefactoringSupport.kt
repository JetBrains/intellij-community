// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFindUsagesHandlerFactory
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.isAncestorOf
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitReceiver
import org.jetbrains.kotlin.types.expressions.DoubleColonLHS

internal class K1MoveRefactoringSupport : KotlinMoveRefactoringSupport {
    override fun findReferencesToHighlight(target: PsiElement, searchScope: SearchScope): Collection<PsiReference> {
        return KotlinFindUsagesHandlerFactory(target.project).createFindUsagesHandler(target, false)
            .findReferencesToHighlight(target, searchScope)
    }

    override fun isExtensionRef(expr: KtSimpleNameExpression): Boolean {
        val resolvedCall = expr.getResolvedCall(expr.analyze(BodyResolveMode.PARTIAL)) ?: return false
        if (resolvedCall is VariableAsFunctionResolvedCall) {
            return resolvedCall.variableCall.candidateDescriptor.isExtension || resolvedCall.functionCall.candidateDescriptor.isExtension
        }
        return resolvedCall.candidateDescriptor.isExtension
    }

    override fun isQualifiable(callableReferenceExpression: KtCallableReferenceExpression): Boolean {
        val receiverExpression = callableReferenceExpression.receiverExpression
        val lhs = callableReferenceExpression.analyze(BodyResolveMode.PARTIAL)[BindingContext.DOUBLE_COLON_LHS, receiverExpression]
        return lhs is DoubleColonLHS.Type
    }

    override fun traverseOuterInstanceReferences(
        member: KtNamedDeclaration,
        stopAtFirst: Boolean,
        body: (OuterInstanceReferenceUsageInfo) -> Unit
    ): Boolean {
        if (member is KtObjectDeclaration || member is KtClass && !member.isInner()) return false
        val context = member.analyzeWithContent()
        val containingClassOrObject = member.containingClassOrObject ?: return false
        val outerClassDescriptor = containingClassOrObject.unsafeResolveToDescriptor() as ClassDescriptor
        var found = false
        member.accept(object : PsiRecursiveElementWalkingVisitor() {
            private fun getOuterInstanceReference(element: PsiElement): OuterInstanceReferenceUsageInfo? {
                return when (element) {
                    is KtThisExpression -> {
                        val descriptor = context[BindingContext.REFERENCE_TARGET, element.instanceReference]
                        val isIndirect = when {
                            descriptor == outerClassDescriptor -> false
                            descriptor?.isAncestorOf(outerClassDescriptor, true) ?: false -> true
                            else -> return null
                        }
                        OuterInstanceReferenceUsageInfo.ExplicitThis(element, isIndirect)
                    }
                    is KtSimpleNameExpression -> {
                        val resolvedCall = element.getResolvedCall(context) ?: return null
                        val dispatchReceiver = resolvedCall.dispatchReceiver as? ImplicitReceiver
                        val extensionReceiver = resolvedCall.extensionReceiver as? ImplicitReceiver
                        var isIndirect = false
                        val isDoubleReceiver = when (outerClassDescriptor) {
                            dispatchReceiver?.declarationDescriptor -> extensionReceiver != null
                            extensionReceiver?.declarationDescriptor -> dispatchReceiver != null
                            else -> {
                                isIndirect = true
                                when {
                                    dispatchReceiver?.declarationDescriptor?.isAncestorOf(outerClassDescriptor, true) ?: false ->
                                        extensionReceiver != null

                                    extensionReceiver?.declarationDescriptor?.isAncestorOf(outerClassDescriptor, true) ?: false ->
                                        dispatchReceiver != null

                                    else -> return null
                                }
                            }
                        }
                        OuterInstanceReferenceUsageInfo.ImplicitReceiver(resolvedCall.call.callElement, isIndirect, isDoubleReceiver)
                    }
                    else -> null
                }
            }

            override fun visitElement(element: PsiElement) {
                getOuterInstanceReference(element)?.let {
                    body(it)
                    found = true
                    if (stopAtFirst) stopWalking()
                    return
                }
                super.visitElement(element)
            }
        })
        return found
    }
}