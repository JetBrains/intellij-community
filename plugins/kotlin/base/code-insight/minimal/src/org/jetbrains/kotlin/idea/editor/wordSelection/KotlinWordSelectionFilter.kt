// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor.wordSelection

import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.KtNodeTypes.BLOCK
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.kdoc.parser.KDocElementTypes
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtContainerNode

class KotlinWordSelectionFilter : Condition<PsiElement> {
    override fun value(e: PsiElement): Boolean {
        if (e.language != KotlinLanguage.INSTANCE) return true

        if (KotlinListSelectioner.canSelect(e)) return false
        if (KotlinCodeBlockSelectioner.canSelect(e)) return false

        val elementType = e.node.elementType
        if (elementType == KtTokens.REGULAR_STRING_PART || elementType == KtTokens.ESCAPE_SEQUENCE) return true

        if (e is KtContainerNode) return false

        return when (e.node.elementType) {
            BLOCK, KDocElementTypes.KDOC_SECTION -> false
            else -> true
        }
    }
}
