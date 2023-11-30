// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.DynamicBundle
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usageView.UsageViewUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

class MoveFilesWithDeclarationsViewDescriptor(
    private val elementsToMove: Array<PsiElement>,
    newParent: PsiDirectory
) : UsageViewDescriptor {
    @Suppress("HardCodedStringLiteral")
    @Nls
    private val processedElementsHeader = if (elementsToMove.size == 1) {
        RefactoringBundle.message(
            "move.single.element.elements.header",
            UsageViewUtil.getType(elementsToMove.first()),
            newParent.virtualFile.presentableUrl
        )
    } else {
        RefactoringBundle.message("move.files.elements.header", newParent.virtualFile.presentableUrl)
    }.replaceFirstChar { if (it.isLowerCase()) it.titlecase(DynamicBundle.getLocale()) else it.toString() }

    @Nls
    private val codeReferencesText = if (elements.size == 1) KotlinBundle.message(
        "text.references.in.code.to.0.1.and.its.declarations",
        UsageViewUtil.getType(elementsToMove.first()),
        UsageViewUtil.getLongName(elementsToMove.first())
    ) else RefactoringBundle.message("references.found.in.code")

    override fun getElements() = elementsToMove

    override fun getProcessedElementsHeader() = processedElementsHeader

    override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): String {
        return codeReferencesText + UsageViewBundle.getReferencesString(usagesCount, filesCount)
    }

    override fun getCommentReferencesText(usagesCount: Int, filesCount: Int): String =
        RefactoringBundle.message("comments.elements.header", UsageViewBundle.getOccurencesString(usagesCount, filesCount))
}