// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.codeInsight

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ThreeState
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal class KotlinImportedScriptCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(editor: Editor, contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
        if (psiFile is KtFile && (psiFile.isScript() || psiFile.name.endsWith(".kts"))) {
            if (contextElement is PsiComment || contextElement.getParentOfType<KtStringTemplateExpression>(false) != null) {
                return ThreeState.UNSURE
            }
            return ThreeState.NO
        }
        return ThreeState.UNSURE
    }
}
