// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinPsiOnlyQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtDeclarationModifierList
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPropertyAccessor

class RemoveAnnotationFix(@Nls private val text: String, annotationEntry: KtAnnotationEntry) :
    KotlinPsiOnlyQuickFixAction<KtAnnotationEntry>(annotationEntry) {

    override fun getText(): String = text

    override fun getFamilyName(): String = text

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? = element

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        element?.delete()
    }

    object JvmOverloads : QuickFixesPsiBasedFactory<KtAnnotationEntry>(KtAnnotationEntry::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: KtAnnotationEntry): List<IntentionAction> =
            listOf(RemoveAnnotationFix(KotlinBundle.message("remove.jvmoverloads.annotation"), psiElement))
    }

    object JvmField : QuickFixesPsiBasedFactory<KtAnnotationEntry>(KtAnnotationEntry::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: KtAnnotationEntry): List<IntentionAction> =
            listOf(RemoveAnnotationFix(KotlinBundle.message("remove.jvmfield.annotation"), psiElement))
    }

    object ExtensionFunctionType : QuickFixesPsiBasedFactory<KtAnnotationEntry>(KtAnnotationEntry::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: KtAnnotationEntry): List<IntentionAction> =
            listOf(RemoveAnnotationFix(KotlinBundle.message("remove.extension.function.type.annotation"), psiElement))
    }

    object UseSiteGetDoesntHaveAnyEffect : AbstractUseSiteGetDoesntHaveAnyEffectQuickFixesFactory() {
        override fun doCreateQuickFixImpl(psiElement: KtAnnotationEntry): IntentionAction =
            RemoveAnnotationFix(KotlinBundle.message("remove.annotation.doesnt.have.any.effect"), psiElement)
    }

    object RemoveForbiddenOptInRetention : QuickFixesPsiBasedFactory<KtAnnotationEntry>(KtAnnotationEntry::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: KtAnnotationEntry): List<IntentionAction> =
            listOf(RemoveAnnotationFix(KotlinBundle.message("fix.opt_in.remove.forbidden.retention"), psiElement))
    }

    companion object : QuickFixesPsiBasedFactory<KtAnnotationEntry>(KtAnnotationEntry::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: KtAnnotationEntry): List<IntentionAction> =
            listOf(RemoveAnnotationFix(KotlinBundle.message("fix.remove.annotation.text"), annotationEntry = psiElement))
    }
}

abstract class AbstractUseSiteGetDoesntHaveAnyEffectQuickFixesFactory :
    QuickFixesPsiBasedFactory<KtAnnotationEntry>(KtAnnotationEntry::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
    final override fun doCreateQuickFix(psiElement: KtAnnotationEntry): List<IntentionAction> =
        when (psiElement.let { it.parent as? KtDeclarationModifierList }?.let { it.parent as? KtPropertyAccessor }?.isGetter == true &&
                psiElement.useSiteTarget?.getAnnotationUseSiteTarget() == AnnotationUseSiteTarget.PROPERTY_GETTER) {
            true -> listOf(doCreateQuickFixImpl(psiElement))
            false -> emptyList()
        }

    protected abstract fun doCreateQuickFixImpl(psiElement: KtAnnotationEntry): IntentionAction
}
