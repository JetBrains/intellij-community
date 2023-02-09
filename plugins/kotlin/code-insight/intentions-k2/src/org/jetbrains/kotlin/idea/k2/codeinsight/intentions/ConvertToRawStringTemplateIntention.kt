// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.descendantsOfType
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.buildStringTemplateForBinaryExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.canBeConvertedToStringLiteral
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.convertToStringLiteral
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isFirstStringPlusExpressionWithoutNewLineInOperands
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal class ConvertToRawStringTemplateIntention :
    AbstractKotlinApplicableIntentionWithContext<KtBinaryExpression, ConvertToRawStringTemplateIntention.Context>(
        KtBinaryExpression::class
    ) {
    class Context(var replacement: SmartPsiElementPointer<KtStringTemplateExpression>)

    override fun getFamilyName() = KotlinBundle.message("convert.concatenation.to.raw.string")

    override fun getActionName(element: KtBinaryExpression, context: Context) = familyName

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtBinaryExpression> = ApplicabilityRanges.SELF

    override fun apply(element: KtBinaryExpression, context: Context, project: Project, editor: Editor?) {
        val replaced = context.replacement.element?.let { element.replaced(it) } ?: return
        convertToStringLiteral(replaced, editor)
    }

    context(KtAnalysisSession)
    override fun prepareContext(element: KtBinaryExpression): Context? {
        if (!isFirstStringPlusExpressionWithoutNewLineInOperands(element)) return null
        return Context(buildStringTemplateForBinaryExpression(element).createSmartPointer())
    }

    override fun isApplicableByPsi(element: KtBinaryExpression) =
        element.descendantsOfType<KtStringTemplateExpression>().all { it.canBeConvertedToStringLiteral() }
}