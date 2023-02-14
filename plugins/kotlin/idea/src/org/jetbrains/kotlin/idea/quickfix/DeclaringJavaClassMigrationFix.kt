// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinPsiOnlyQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityChecker
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

class DeclaringJavaClassMigrationFix(element: PsiElement) : KotlinPsiOnlyQuickFixAction<PsiElement>(element) {
    companion object : QuickFixesPsiBasedFactory<PsiElement>(PsiElement::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: PsiElement): List<IntentionAction> = listOf(DeclaringJavaClassMigrationFix(psiElement))
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val declaringJavaClassElement = KtPsiFactory(project).createExpression("declaringJavaClass")
        element?.replace(declaringJavaClassElement)
    }

    override fun getText(): String = familyName
    override fun getFamilyName(): String = KotlinBundle.message("fix.replace.with.declaring.java.class")
}