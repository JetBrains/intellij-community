// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractGenerateCommandProvider
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtFile

internal class KotlinGenerateCommandCompletion : AbstractGenerateCommandProvider() {
    override fun generationIsAvailable(element: PsiElement, offset: Int): Boolean {
        val parent = element.parent
        if (parent !is KtClass && parent !is KtFile && parent !is KtClassBody) return false
        val onlySpaceInLine = isOnlySpaceInLine(element.containingFile.fileDocument, offset)
        if (onlySpaceInLine) return true
        if (parent is KtClass && parent.nameIdentifier == element) return true
        return false
    }
}