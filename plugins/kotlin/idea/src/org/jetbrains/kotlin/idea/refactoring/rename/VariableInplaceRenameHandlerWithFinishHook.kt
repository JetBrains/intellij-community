// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer

class VariableInplaceRenameHandlerWithFinishHook(private val onFinish: () -> Unit) : KotlinVariableInplaceRenameHandler() {
    override fun createRenamer(elementToRename: PsiElement, editor: Editor): VariableInplaceRenamer {
        return object : RenamerImpl(elementToRename as PsiNamedElement, editor) {
            override fun performRefactoring(): Boolean {
                try {
                    return super.performRefactoring()
                } finally {
                    onFinish()
                }
            }
        }
    }
}