// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

class KotlinFormattingModelBuilder : FormattingModelBuilder {
    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val settings = formattingContext.codeStyleSettings
        val containingFile = formattingContext.containingFile
        val block = KotlinBlock(
            containingFile.node,
            NodeAlignmentStrategy.getNullStrategy(),
            Indent.getNoneIndent(),
            wrap = null,
            settings,
            createSpacingBuilder(settings, KotlinSpacingBuilderUtilImpl)
        )

        return FormattingModelProvider.createFormattingModelForPsiFile(containingFile, block, settings)
    }

    override fun getRangeAffectingIndent(psiFile: PsiFile, i: Int, astNode: ASTNode): TextRange? {
        return null
    }
}
