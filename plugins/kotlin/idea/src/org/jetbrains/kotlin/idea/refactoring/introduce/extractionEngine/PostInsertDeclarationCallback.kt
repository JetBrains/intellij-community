// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.kotlin.psi.KtPsiFactory

interface PostInsertDeclarationCallback {
    /**
     * Executed every time extraction engine inserts a declaration into PSI.
     * This method is called under **write action**
     *
     * @param declaration inserted declaration.
     * @param targetContainer the container where the declaration is inserted.
     * @param psiFactory the factory used to create Kotlin PSI elements.
     */
    @RequiresWriteLock
    fun declarationInserted(declaration: PsiElement, targetContainer: PsiElement, psiFactory: KtPsiFactory)

    companion object {
        val EP_NAME = ExtensionPointName.create<PostInsertDeclarationCallback>("org.jetbrains.kotlin.postInsertDeclarationCallback")
    }
}
