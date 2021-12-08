// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaClassDescriptor
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.intentions.receiverType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.getThisReceiverOwner
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.descriptorUtil.isSubclassOf
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.synthetic.SyntheticJavaPropertyDescriptor
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class RedundantInnerClassModifierInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = classVisitor(fun(targetClass) {
        val innerModifier = targetClass.modifierList?.getModifier(KtTokens.INNER_KEYWORD) ?: return
        if (targetClass.containingClassOrObject.safeAs<KtObjectDeclaration>()?.isObjectLiteral() == true) return
        val outerClasses = targetClass.parentsOfType<KtClass>().dropWhile { it == targetClass }.toSet()
        if (outerClasses.isEmpty() || outerClasses.any { it.isLocal || it.isInner() }) return
        if (targetClass.hasOuterClassMemberReference(outerClasses)) return
        holder.registerProblem(
            innerModifier,
            KotlinBundle.message("inspection.redundant.inner.class.modifier.descriptor"),
            ProblemHighlightType.LIKE_UNUSED_SYMBOL,
            RemoveInnerModifierFix()
        )
    })

    private fun KtClass.hasOuterClassMemberReference(outerClasses: Set<KtClass>): Boolean {
        val targetClass = this
        val outerClassDescriptors by lazy {
            val context = targetClass.analyze(BodyResolveMode.PARTIAL)
            outerClasses.mapNotNull { context[BindingContext.DECLARATION_TO_DESCRIPTOR, it] as? ClassDescriptor }
        }
        val hasSuperType = outerClasses.any { it.getSuperTypeList() != null }
        return anyDescendantOfType<KtExpression> { expression ->
            when (expression) {
                is KtNameReferenceExpression -> {
                    val reference = expression.mainReference.resolve()?.let {
                        (it as? KtConstructor<*>)?.containingClass() ?: it
                    }
                    if (reference is PsiClass && reference.parent is PsiClass) {
                        return@anyDescendantOfType reference.getJavaClassDescriptor()?.isInner == true
                    }
                    if (reference is KtObjectDeclaration || (reference as? KtDeclaration)?.containingClassOrObject is KtObjectDeclaration) {
                        return@anyDescendantOfType false
                    }
                    val referenceContainingClass = reference?.getStrictParentOfType<KtClass>()
                    if (referenceContainingClass != null) {
                        if (referenceContainingClass == targetClass) return@anyDescendantOfType false
                        if (referenceContainingClass in outerClasses) {
                            val parentQualified = (expression.parent as? KtCallExpression ?: expression).getQualifiedExpressionForSelector()
                            if (parentQualified != null && !parentQualified.hasThisReceiverOfOuterClass(outerClassDescriptors)) {
                                val receiverTypeOfReference = reference.receiverTypeReference()
                                if (receiverTypeOfReference == null) {
                                    return@anyDescendantOfType false
                                } else {
                                    val context = parentQualified.analyze(BodyResolveMode.PARTIAL)
                                    val receiverOwnerType = parentQualified.getResolvedCall(context)?.dispatchReceiver
                                        ?.getThisReceiverOwner(context)?.safeAs<CallableDescriptor>()?.receiverType()
                                    if (receiverOwnerType == context[BindingContext.TYPE, receiverTypeOfReference]) {
                                        return@anyDescendantOfType false
                                    }
                                }
                            }
                            return@anyDescendantOfType reference !is KtClass || reference.isInner()
                        }
                    }
                    if (!hasSuperType) return@anyDescendantOfType false
                    val referenceClassDescriptor = referenceContainingClass?.descriptor as? ClassDescriptor
                        ?: reference?.getStrictParentOfType<PsiClass>()?.getJavaClassDescriptor()
                        ?: (expression.resolveToCall()?.resultingDescriptor as? SyntheticJavaPropertyDescriptor)
                            ?.getMethod?.containingDeclaration as? ClassDescriptor
                        ?: return@anyDescendantOfType false
                    outerClassDescriptors.any { outer -> outer.isSubclassOf(referenceClassDescriptor) }
                }
                is KtThisExpression -> expression.referenceClassDescriptor() in outerClassDescriptors
                else -> false
            }
        }
    }

    private fun KtQualifiedExpression.hasThisReceiverOfOuterClass(outerClassDescriptors: List<ClassDescriptor>): Boolean {
        return parent !is KtQualifiedExpression
                && receiverExpression is KtThisExpression
                && receiverExpression.referenceClassDescriptor() in outerClassDescriptors
    }

    private fun KtExpression.referenceClassDescriptor(): ClassifierDescriptor? {
        return resolveToCall()?.resultingDescriptor?.returnType?.constructor?.declarationDescriptor
    }

    private fun PsiElement.receiverTypeReference(): KtTypeReference? {
        return safeAs<KtNamedFunction>()?.receiverTypeReference ?: safeAs<KtProperty>()?.receiverTypeReference
    }

    private class RemoveInnerModifierFix : LocalQuickFix {
        override fun getName() = KotlinBundle.message("remove.redundant.0.modifier", KtTokens.INNER_KEYWORD.value)

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val targetClass = descriptor.psiElement.getStrictParentOfType<KtClass>() ?: return
            val fixedElements = fixReceiverOnCallSite(targetClass)
            targetClass.removeModifier(KtTokens.INNER_KEYWORD)
            fixedElements.filterIsInstance<KtQualifiedExpression>().forEach { ShortenReferences.DEFAULT.process(it) }
        }

        private fun fixReceiverOnCallSite(targetClass: KtClass): List<PsiElement> {
            val containingClass = targetClass.getStrictParentOfType<KtClass>() ?: return emptyList()
            val bindingContext = containingClass.analyze(BodyResolveMode.PARTIAL)
            val fqName =
                bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, containingClass]?.fqNameOrNull()?.asString() ?: return emptyList()
            val psiFactory = KtPsiFactory(targetClass)
            val newReceiver = psiFactory.createExpression(fqName)
            return ReferencesSearch.search(targetClass, targetClass.useScope).mapNotNull {
                val callExpression = it.element.parent as? KtCallExpression ?: return@mapNotNull null
                val qualifiedExpression = callExpression.getQualifiedExpressionForSelector()
                val parentClass = callExpression.getStrictParentOfType<KtClass>()
                when {
                    // Explicit receiver
                    qualifiedExpression != null ->
                        if (parentClass == containingClass) {
                            qualifiedExpression.replace(callExpression)
                        } else {
                            qualifiedExpression.receiverExpression.replace(newReceiver)
                        }
                    // Implicit receiver
                    else -> callExpression.replace(psiFactory.createExpressionByPattern("$0.$1", newReceiver, callExpression))
                }
            }
        }
    }
}
