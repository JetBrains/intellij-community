// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.usageView.UsageViewBundle
import com.intellij.usageView.UsageViewDescriptor
import org.jetbrains.annotations.Nls

class KotlinUsagesViewDescriptor(private val element: PsiElement, @Nls private val elementsHeader: String) : UsageViewDescriptor {
    override fun getElements(): Array<PsiElement> = arrayOf(element)

    override fun getProcessedElementsHeader(): String = elementsHeader

    override fun getCodeReferencesText(usagesCount: Int, filesCount: Int): String =
        RefactoringBundle.message("references.to.be.changed", UsageViewBundle.getReferencesString(usagesCount, filesCount))

    override fun getCommentReferencesText(usagesCount: Int, filesCount: Int): String =
        RefactoringBundle.message("comments.elements.header", UsageViewBundle.getOccurencesString(usagesCount, filesCount))
}
