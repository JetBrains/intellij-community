// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.actions.ActionRequest
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer

internal abstract class CreateKotlinElementAction(
    target: KtClassOrObject,
    protected open val request: ActionRequest
) : IntentionAction {

    protected val myTargetPointer = target.createSmartPointer()

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return myTargetPointer.element != null && request.isValid
    }

    protected val target: KtClassOrObject
        get() = requireNotNull(myTargetPointer.element) {
            "Don't access this property if isAvailable() returned false"
        }

    open fun getTarget(): JvmClass = target.toLightClass()!!

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? = target

    override fun startInWriteAction(): Boolean = true
}
