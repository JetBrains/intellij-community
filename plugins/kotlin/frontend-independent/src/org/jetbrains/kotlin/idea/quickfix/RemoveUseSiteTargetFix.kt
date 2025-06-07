// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.createSmartPointer
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.siblings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.inspections.AbstractUseSiteGetDoesntHaveAnyEffectQuickFixesFactory
import org.jetbrains.kotlin.psi.KtAnnotationEntry

class RemoveUseSiteTargetFix(annotationEntry: KtAnnotationEntry) : PsiUpdateModCommandAction<KtAnnotationEntry>(annotationEntry) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.use.site.get.target")
    override fun invoke(context: ActionContext, element: KtAnnotationEntry, updater: ModPsiUpdater) {
        val useSiteTarget = element.useSiteTarget
        useSiteTarget?.siblings()
            ?.takeWhile { it === useSiteTarget || it is PsiWhiteSpace || it is PsiComment || it is LeafPsiElement && it.text == ":" }
            ?.map(PsiElement::createSmartPointer)
            ?.toList()
            ?.forEach { it.element?.delete() }
    }

    object UseSiteGetDoesntHaveAnyEffect : AbstractUseSiteGetDoesntHaveAnyEffectQuickFixesFactory() {
        override fun doCreateQuickFixImpl(psiElement: KtAnnotationEntry): IntentionAction =
            RemoveUseSiteTargetFix(psiElement).asIntention()
    }
}
