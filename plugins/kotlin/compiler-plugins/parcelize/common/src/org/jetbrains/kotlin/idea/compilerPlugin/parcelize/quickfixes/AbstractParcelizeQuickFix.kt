// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinPsiOnlyQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

abstract class AbstractParcelizeQuickFix<T : KtElement>(element: T) : KotlinQuickFixAction<T>(element) {
    protected companion object {
        fun KtElement.shortenReferences() {
            ShortenReferencesFacility.getInstance().shorten(this)
        }
    }

    override fun getFamilyName() = text

    abstract fun invoke(ktPsiFactory: KtPsiFactory, element: T)

    final override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val clazz = element ?: return
        val ktPsiFactory = KtPsiFactory(project, markGenerated = true)
        invoke(ktPsiFactory, clazz)
    }
}

abstract class AbstractParcelizePsiOnlyQuickFix<T : KtElement>(element: T) : KotlinPsiOnlyQuickFixAction<T>(element) {
    protected companion object {
        fun KtElement.shortenReferences() {
            ShortenReferencesFacility.getInstance().shorten(this)
        }
    }

    override fun getFamilyName() = text

    abstract fun invoke(ktPsiFactory: KtPsiFactory, element: T)

    final override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val clazz = element ?: return
        val ktPsiFactory = KtPsiFactory(project, markGenerated = true)
        invoke(ktPsiFactory, clazz)
    }
}

abstract class AbstractQuickFixFactory(private val f: Diagnostic.() -> IntentionAction?) : KotlinSingleIntentionActionFactory() {
    companion object {
        inline fun <reified T : KtElement> Diagnostic.findElement() = psiElement.getNonStrictParentOfType<T>()
    }

    override fun createAction(diagnostic: Diagnostic) = f(diagnostic)
}
