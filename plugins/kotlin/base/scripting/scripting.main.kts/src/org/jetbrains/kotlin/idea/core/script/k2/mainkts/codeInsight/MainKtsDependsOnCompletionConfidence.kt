// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.mainkts.codeInsight

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MainKtsDependsOnCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(editor: Editor, contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
        if (!DEPENDS_ON_PATTERN.accepts(contextElement)) return ThreeState.UNSURE
        val start = findCoordinateStart(psiFile.text, offset)

        return if (offset - start >= 2) ThreeState.NO else ThreeState.UNSURE
    }
}
