// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
abstract class KotlinQuickFixAction<out T : PsiElement>(element: T) : QuickFixActionBase<T>(element) {
    protected open fun isAvailable(project: Project, editor: Editor?, file: KtFile) = true

    override fun isAvailableImpl(project: Project, editor: Editor?, file: PsiFile): Boolean {
        val ktFile = file as? KtFile ?: return false
        return isAvailable(project, editor, ktFile)
    }

    final override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val element = element ?: return
        if (file is KtFile && IntentionPreviewUtils.prepareElementForWrite(element)) {
            invoke(project, editor, file)
        }
    }

    protected abstract operator fun invoke(project: Project, editor: Editor?, file: KtFile)

    override fun startInWriteAction() = true
}
