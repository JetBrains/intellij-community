// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.isInsideAnnotationEntryArgumentList
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.convertConcatenationToBuildStringCall
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression

internal class ConvertConcatenationToBuildStringIntention :
    KotlinApplicableModCommandAction<KtBinaryExpression, Unit>(KtBinaryExpression::class) {

    override fun getFamilyName(): String = KotlinBundle.message("convert.concatenation.to.build.string")

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean =
        element.operationToken == KtTokens.PLUS && !element.isInsideAnnotationEntryArgumentList()

    override fun KaSession.prepareContext(element: KtBinaryExpression): Unit? {
        val parent = element.parent
        val isApplicable = element.expressionType?.isStringType == true
                && (parent !is KtBinaryExpression
                || parent.operationToken != KtTokens.PLUS
                || parent.expressionType?.isStringType == false)
        return isApplicable.asUnit
    }

    override fun invoke(
      actionContext: ActionContext,
      element: KtBinaryExpression,
      elementContext: Unit,
      updater: ModPsiUpdater,
    ) {
        val buildStringCall = convertConcatenationToBuildStringCall(element)
        shortenReferences(buildStringCall)
    }
}