// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractSurroundWithCompletionCommandProvider
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.lang.folding.CustomFoldingSurroundDescriptor
import com.intellij.lang.surroundWith.ModCommandSurrounder
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.findTopmostParentInFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPackageDirective

internal class KotlinSurroundWithCompletionCommandProvider : AbstractSurroundWithCompletionCommandProvider() {
    override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?, surrounder: Surrounder): Boolean {
        return surrounder is ModCommandSurrounder && surrounder !is CustomFoldingSurroundDescriptor.CustomFoldingRegionSurrounder
    }

    override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
        var currentCommandContext = getCommandContext(offset, psiFile) ?: return false
        var currentOffset = offset
        if (currentCommandContext is PsiWhiteSpace) {
            currentCommandContext = PsiTreeUtil.skipWhitespacesBackward(currentCommandContext) ?: return false
            currentOffset = currentCommandContext.endOffset
        }
        val expression =
            try {
                currentCommandContext.findTopmostParentInFile(withSelf = true) {
                    it is KtExpression && it.textRange.endOffset == currentOffset &&
                            it.parentOfType<KtExpression>() != null &&
                            it.parentOfType<KtPackageDirective>() == null &&
                            analyze(it) { !it.isUsedAsExpression }
                } ?: return false
            } catch (_: Exception) {
                return false
            }
        val parentExpression = expression.parentOfType<KtExpression>() ?: return true
        val fileDocument = psiFile.fileDocument
        return fileDocument.getLineNumber(expression.textRange.endOffset) !=
                fileDocument.getLineNumber(parentExpression.textRange.endOffset)
    }
}