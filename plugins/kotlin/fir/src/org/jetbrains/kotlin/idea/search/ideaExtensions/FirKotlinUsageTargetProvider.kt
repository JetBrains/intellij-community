// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageTargetProvider
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor

class FirKotlinUsageTargetProvider : UsageTargetProvider, DumbAware {
    override fun getTargets(editor: Editor, file: PsiFile): Array<UsageTarget?>? {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(TargetElementUtil.adjustOffset(file, editor.document, offset))
        if (element == null) return null

        if (element.elementType == KtTokens.VAL_KEYWORD || element.elementType == KtTokens.VAR_KEYWORD) {
            (element.parent as? KtParameter)?.let { param ->
                (param.ownerFunction as? KtPrimaryConstructor)?.let {
                    return arrayOf(PsiElement2UsageTargetAdapter(it, false))
                }
            }
        }
        return null
    }
}
