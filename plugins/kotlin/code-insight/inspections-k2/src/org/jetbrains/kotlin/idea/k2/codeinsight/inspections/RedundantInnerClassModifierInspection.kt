// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.options.JavaClassValidator
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentsOfType
import com.intellij.util.containers.OrderedSet
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.k2.refactoring.getThisReceiverOwner
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class RedundantInnerClassModifierInspection : AbstractKotlinInspection() {
    @Suppress("MemberVisibilityCanBePrivate")
    var ignorableAnnotations: OrderedSet<String> = OrderedSet(listOf(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_NESTED))

    override fun getOptionsPane(): OptPane {
        return OptPane.pane(OptPane.stringList("ignorableAnnotations", InspectionGadgetsBundle.message("ignore.if.annotated.by"),
                                               JavaClassValidator().annotationsOnly()))
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitClass(klass: KtClass) {
                super.visitClass(klass)

                val innerModifier = klass.modifierList?.getModifier(KtTokens.INNER_KEYWORD) ?: return
                if (klass.containingClassOrObject.safeAs<KtObjectDeclaration>()?.isObjectLiteral() == true) return
                val outerClasses = klass.parentsOfType<KtClass>().dropWhile { it == klass }.toSet()
                if (outerClasses.isEmpty() || outerClasses.any { it.isLocal || it.isInner() }) return

                analyze(klass) {
                    if (hasIgnorableAnnotations(klass) || hasOuterClassMemberReference(klass, outerClasses)) {
                        return
                    }
                }

                holder.registerProblem(
                    innerModifier,
                    KotlinBundle.message("inspection.redundant.inner.class.modifier.descriptor"),
                    RemoveInnerModifierFix()
                )
            }
        }
    }

    private fun KaSession.hasIgnorableAnnotations(klass: KtClass): Boolean {
        if (ignorableAnnotations.isEmpty()) return false
        val ignorableAnnotationFqNames = ignorableAnnotations.associate {
            val fqName = FqName(it)
            fqName.shortName().asString() to fqName
        }

        val classSymbol = klass.symbol as? KaClassSymbol ?: return false
        return classSymbol.annotations.any { annotation ->
            val annotationFqName = annotation.classId?.asFqNameString() ?: return@any false
            val shortName = annotationFqName.split(".").last()
            ignorableAnnotationFqNames[shortName]?.asString() == annotationFqName
        }
    }

    private fun KaSession.hasOuterClassMemberReference(targetClass: KtClass, outerClasses: Set<KtClass>): Boolean {
        val outerClassSymbols by lazy {
            outerClasses.mapNotNull { it.symbol as? KaClassSymbol }
        }
        val hasSuperType = outerClasses.any { it.getSuperTypeList() != null }
        return targetClass.anyDescendantOfType<KtExpression> { expression ->
            when (expression) {
                is KtNameReferenceExpression -> expression.isReferenceToOuterClass(
                    targetClass,
                    outerClasses,
                    outerClassSymbols,
                    hasSuperType
                )
                is KtThisExpression -> expression.instanceReference.mainReference.resolveToSymbol() in outerClassSymbols
                else -> false
            }
        }
    }

    context(KaSession)
    private fun KtNameReferenceExpression.isReferenceToOuterClass(
        innerClass: KtClass,
        outerClasses: Set<KtClass>,
        outerClassSymbols: List<KaClassSymbol>,
        hasSuperType: Boolean
    ): Boolean {
        val resolvedElement = mainReference.resolve()?.let {
            (it as? KtConstructor<*>)?.containingClass() ?: it
        }
        if (resolvedElement is PsiClass && resolvedElement.parent is PsiClass) {
            return resolvedElement.namedClassSymbol?.isInner == true
        }
        if (resolvedElement is KtObjectDeclaration || (resolvedElement as? KtDeclaration)?.containingClassOrObject is KtObjectDeclaration) {
            return false
        }
        val referenceContainingClass = resolvedElement?.getStrictParentOfType<KtClass>()
        if (referenceContainingClass != null) {
            if (referenceContainingClass == innerClass) return false
            if (referenceContainingClass in outerClasses) {
                val parentQualified = (parent as? KtCallExpression ?: this).getQualifiedExpressionForSelector()
                if (parentQualified != null && !parentQualified.hasThisReceiverOfOuterClass(outerClassSymbols)) {
                    val receiverTypeOfReference = (resolvedElement as? KtCallableDeclaration)?.receiverTypeReference
                    if (receiverTypeOfReference == null) {
                        return false
                    } else {
                        val callableMemberCall = parentQualified.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()
                        val dispatchReceiver = callableMemberCall?.partiallyAppliedSymbol?.dispatchReceiver
                        val receiverOwnerType = (dispatchReceiver?.getThisReceiverOwner() as? KaCallableSymbol)?.receiverType
                        if (receiverOwnerType != null && receiverTypeOfReference.type.semanticallyEquals(receiverOwnerType)) {
                            return false
                        }
                    }
                }
                return resolvedElement !is KtClass || resolvedElement.isInner()
            }
        }
        if (!hasSuperType) return false
        val referenceClassDescriptor = referenceContainingClass?.symbol as? KaClassSymbol
            ?: resolvedElement?.getStrictParentOfType<PsiClass>()?.namedClassSymbol ?: return false
        return outerClassSymbols.any { outer -> outer.isSubClassOf(referenceClassDescriptor) }
    }

    context(KaSession)
    private fun KtQualifiedExpression.hasThisReceiverOfOuterClass(outerClassSymbols: List<KaClassSymbol>): Boolean {
        return parent !is KtQualifiedExpression
                && receiverExpression is KtThisExpression
                && receiverExpression.mainReference?.resolveToSymbol() in outerClassSymbols
    }

    private class RemoveInnerModifierFix : LocalQuickFix {
        override fun getName() = familyName

        override fun getFamilyName() = KotlinBundle.message("remove.redundant.0.modifier", KtTokens.INNER_KEYWORD.value)

        override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
            val targetClass = previewDescriptor.psiElement.getStrictParentOfType<KtClass>() ?: return IntentionPreviewInfo.EMPTY
            val stopOffset = targetClass.body?.lBrace?.startOffsetInParent ?: targetClass.textLength
            val classSignature = targetClass.text.substring(0, stopOffset)
            return IntentionPreviewInfo.CustomDiff(KotlinFileType.INSTANCE, classSignature, classSignature.replace("inner ", ""))
        }

        override fun startInWriteAction(): Boolean {
            return false
        }

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val targetClass = descriptor.psiElement.getStrictParentOfType<KtClass>() ?: return
            val allToModify = runWithModalProgressBlocking(project, QuickFixBundle.message("searching.for.usages.progress.title")) {
                readAction {
                    ReferencesSearch.search(targetClass, targetClass.useScope).findAll().mapNotNull { it.element }
                }
            }
            val files = (allToModify.mapNotNull { it.containingFile }.distinct() + targetClass.containingFile).toTypedArray()
            WriteCommandAction.writeCommandAction(project, *files).run<Throwable> {
                targetClass.removeModifier(KtTokens.INNER_KEYWORD)
                updateCallSites(allToModify, targetClass).filterIsInstance<KtQualifiedExpression>().forEach { shortenReferences(it) }
            }
        }

        private fun updateCallSites(references: List<PsiElement>, targetClass: KtClass): List<PsiElement> {
            val containingClass = targetClass.getStrictParentOfType<KtClass>() ?: return emptyList()
            val fqName = containingClass.fqName?.asString() ?: return emptyList()
            val psiFactory = KtPsiFactory(targetClass.project)
            val newReceiver = psiFactory.createExpression(fqName)
            return references.mapNotNull {
                val callExpression = it.parent as? KtCallExpression ?: return@mapNotNull null
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
