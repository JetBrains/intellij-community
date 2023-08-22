// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.siblings
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinPsiOnlyQuickFixAction
import org.jetbrains.kotlin.idea.inspections.AbstractUseSiteGetDoesntHaveAnyEffectQuickFixesFactory
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile

class RemoveUseSiteTargetFix(annotationEntry: KtAnnotationEntry) : KotlinPsiOnlyQuickFixAction<KtAnnotationEntry>(annotationEntry) {
    override fun getText(): String = KotlinBundle.message("remove.use.site.get.target")
    override fun getFamilyName(): String = text
    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val useSiteTarget = element?.useSiteTarget
        useSiteTarget?.siblings()
            ?.takeWhile { it === useSiteTarget || it is PsiWhiteSpace || it is PsiComment || it is LeafPsiElement && it.text == ":" }
            ?.map(PsiElement::createSmartPointer)
            ?.toList()
            ?.forEach { it.element?.delete() }
    }

    object UseSiteGetDoesntHaveAnyEffect : AbstractUseSiteGetDoesntHaveAnyEffectQuickFixesFactory() {
        override fun doCreateQuickFixImpl(psiElement: KtAnnotationEntry): IntentionAction = RemoveUseSiteTargetFix(psiElement)
    }
}
