// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixActionBase

abstract class KotlinCrossLanguageQuickFixAction<out T : PsiElement>(element: T) : QuickFixActionBase<T>(element) {
    override val isCrossLanguageFix: Boolean
        get() = true

    final override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val element = element
        if (element != null && IntentionPreviewUtils.prepareElementForWrite(element)) {
            invokeImpl(project, editor, file)
        }
    }

    protected abstract fun invokeImpl(project: Project, editor: Editor?, file: PsiFile)
}
