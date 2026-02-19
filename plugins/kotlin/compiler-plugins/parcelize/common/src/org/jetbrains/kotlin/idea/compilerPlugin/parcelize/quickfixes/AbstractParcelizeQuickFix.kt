// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinPsiOnlyQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixActionBase
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.quickFixesPsiBasedFactory
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

abstract class AbstractParcelizePsiOnlyQuickFix<T : KtElement>(element: T) : KotlinPsiOnlyQuickFixAction<T>(element) {
    override fun getFamilyName() = text

    abstract fun invoke(ktPsiFactory: KtPsiFactory, element: T)

    final override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val clazz = element ?: return
        val ktPsiFactory = KtPsiFactory(project, markGenerated = true)
        invoke(ktPsiFactory, clazz)
    }
}

fun KtElement.shortenReferences() {
    ShortenReferencesFacility.getInstance().shorten(this)
}

inline fun <reified T : KtElement> factory(crossinline constructor: (T) -> QuickFixActionBase<*>?) = quickFixesPsiBasedFactory<PsiElement> {
    listOfNotNull(it.getNonStrictParentOfType<T>()?.let(constructor))
}
