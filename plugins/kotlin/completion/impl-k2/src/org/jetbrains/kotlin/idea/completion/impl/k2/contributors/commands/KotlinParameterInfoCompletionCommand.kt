// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractParameterInfoCompletionCommand
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal class KotlinParameterInfoCompletionCommand : AbstractParameterInfoCompletionCommand() {
    override fun inParameterList(offset: Int, psiFile: PsiFile): Boolean {
        val element = psiFile.findElementAt(offset)
        val parameterList = element?.parentOfType<KtValueArgumentList>() ?: return false
        if (parameterList.parent !is KtCallExpression) return false
        return parameterList.textRange.startOffset < offset && offset < parameterList.textRange.endOffset
    }
}