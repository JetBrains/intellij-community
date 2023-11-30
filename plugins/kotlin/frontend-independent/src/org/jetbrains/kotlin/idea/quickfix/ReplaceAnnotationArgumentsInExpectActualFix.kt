// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile

internal class ReplaceAnnotationArgumentsInExpectActualFix(
    @Nls private val text: String,
    @SafeFieldForPreview private val copyFromAnnotationEntry: KtAnnotationEntry,
    copyToAnnotationEntry: KtAnnotationEntry,
) : KotlinQuickFixAction<KtAnnotationEntry>(copyToAnnotationEntry) {
    override fun getText(): String = text

    override fun getFamilyName(): String = text

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? = element

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val copyToAnnotationEntry = element ?: return
        val newValueArguments = copyFromAnnotationEntry.valueArgumentList?.copy()
        copyToAnnotationEntry.valueArgumentList?.delete()
        if (newValueArguments != null) {
            copyToAnnotationEntry.addAfter(newValueArguments, copyToAnnotationEntry.lastChild)
        }
    }
}