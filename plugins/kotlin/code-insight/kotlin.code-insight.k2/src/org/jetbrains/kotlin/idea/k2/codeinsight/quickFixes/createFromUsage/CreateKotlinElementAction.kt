// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.actions.ActionRequest
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer

internal abstract class CreateKotlinElementAction(
    protected open val request: ActionRequest,
    private val pointerToContainer: SmartPsiElementPointer<*>,
) : IntentionAction {
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return pointerToContainer.element != null && request.isValid
    }

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? = pointerToContainer.element

    override fun startInWriteAction(): Boolean = true
}
