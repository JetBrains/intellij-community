// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.intentions.copyConcatenatedStringToClipboard

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression

internal class CopyConcatenatedStringToClipboardIntention :
    PsiBasedModCommandAction<KtBinaryExpression>(KtBinaryExpression::class.java) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("copy.concatenation.text.to.clipboard")

    override fun getPresentation(context: ActionContext, element: KtBinaryExpression): Presentation? {
        if (element.operationToken != KtTokens.PLUS) return null

        val isString = analyze(element) {
            element.expressionType?.isStringType == true
        }
        if (!isString) return null

        return super.getPresentation(context, element)
    }

    override fun perform(context: ActionContext, element: KtBinaryExpression): ModCommand {
        val text = ConcatenatedStringGenerator().create(element)
        return ModCommand.copyToClipboard(text)
    }
}
