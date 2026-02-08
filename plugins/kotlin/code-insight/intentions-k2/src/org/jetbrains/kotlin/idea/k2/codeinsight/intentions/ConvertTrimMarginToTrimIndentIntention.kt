// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.calculateIndent
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isStartOfLine
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isSurroundedByLineBreaksOrBlanks
import org.jetbrains.kotlin.idea.codeinsights.impl.base.marginPrefix
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

internal class ConvertTrimMarginToTrimIndentIntention :
    KotlinApplicableModCommandAction<KtCallExpression, Unit>(KtCallExpression::class) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("convert.to.trim.indent")

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        val callee = element.calleeExpression ?: return false
        if (callee.text != "trimMargin") return false
        val template = (element.getQualifiedExpressionForSelector()?.receiverExpression as? KtStringTemplateExpression) ?: return false
        if (!template.text.startsWith("\"\"\"")) return false
        if (!template.isSurroundedByLineBreaksOrBlanks()) return false
        val marginPrefix = element.marginPrefix() ?: return false
        return template.entries.all { entry ->
            !entry.isStartOfLine() || entry.text.dropWhile { it.isWhitespace() }.startsWith(marginPrefix)
        }
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Unit? {
        val callee = element.calleeExpression ?: return null
        val symbol = callee.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()?.symbol ?: return null
        return (symbol.callableId?.asSingleFqName() == FqName("kotlin.text.trimMargin")).asUnit
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtCallExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val qualifiedExpression = element.getQualifiedExpressionForSelector()
        val template = (qualifiedExpression?.receiverExpression as? KtStringTemplateExpression) ?: return
        val marginPrefix = element.marginPrefix() ?: return
        val indent = template.calculateIndent()
        val newTemplate = buildString {
            template.entries.forEach { entry ->
                val text = entry.text
                if (entry.isStartOfLine()) {
                    append(indent)
                    append(entry.text.dropWhile { it.isWhitespace() }.replaceFirst(marginPrefix, ""))
                } else {
                    append(text)
                }
            }
        }
        qualifiedExpression.replace(KtPsiFactory(element.project).createExpression("\"\"\"$newTemplate\"\"\".trimIndent()"))
    }
}