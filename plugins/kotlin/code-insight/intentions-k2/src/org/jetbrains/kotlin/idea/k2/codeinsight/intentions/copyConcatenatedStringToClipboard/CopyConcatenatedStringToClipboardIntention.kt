// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions.copyConcatenatedStringToClipboard

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import java.awt.datatransfer.StringSelection

internal class CopyConcatenatedStringToClipboardIntention :
    KotlinApplicableModCommandAction<KtBinaryExpression, Unit>(KtBinaryExpression::class) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("copy.concatenation.text.to.clipboard")

    override fun isFileAllowed(file: PsiFile): Boolean =
        true

    override fun getApplicableRanges(element: KtBinaryExpression): List<TextRange> =
        ApplicabilityRange.self(element)

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean =
        element.operationToken == KtTokens.PLUS

    override fun KaSession.prepareContext(element: KtBinaryExpression): Unit? {
        val isString = element.expressionType?.isStringType == true
        return isString.asUnit
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtBinaryExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val text = ConcatenatedStringGenerator().create(element)
        CopyPasteManager.getInstance().setContents(StringSelection(text))
    }
}
