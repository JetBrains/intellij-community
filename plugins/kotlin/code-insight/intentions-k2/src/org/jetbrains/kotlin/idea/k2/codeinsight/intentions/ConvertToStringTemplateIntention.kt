// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.buildStringTemplateForBinaryExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.containNoNewLine
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isFirstStringPlusExpressionWithoutNewLineInOperands
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * A class for convert-to-string-template intention.
 *
 * Example: "a" + 1 + 'b' + foo + 2.3f + bar -> "a1b${foo}2.3f{bar}"
 */
internal class ConvertToStringTemplateIntention :
    AbstractKotlinApplicableIntentionWithContext<KtBinaryExpression, ConvertToStringTemplateIntention.Context>(
        KtBinaryExpression::class
    ) {
    class Context(val replacement: SmartPsiElementPointer<KtStringTemplateExpression>)

    override fun getFamilyName() = KotlinBundle.message("convert.concatenation.to.template")

    override fun getActionName(element: KtBinaryExpression, context: Context): String = familyName

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtBinaryExpression> = ApplicabilityRanges.SELF
    override fun apply(element: KtBinaryExpression, context: Context, project: Project, editor: Editor?) {
        context.replacement.element?.let { element.replaced(it) }
    }

    /**
     * [element] is applicable for this intention if
     * - it is an expression with only string plus operations, and
     * - its parent is not an expression with only string plus operations
     *   - which helps us to avoid handling the child multiple times
     *     e.g., for "a" + 'b' + "c", we do not want to visit both 'b' + "c" and "a" + 'b' + "c" since 'b' + "c" will be handled
     *     in "a" + 'b' + "c".
     */
    context(KtAnalysisSession)
    override fun prepareContext(element: KtBinaryExpression): Context? {
        if (!isFirstStringPlusExpressionWithoutNewLineInOperands(element)) return null
        return Context(buildStringTemplateForBinaryExpression(element).createSmartPointer())
    }

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        if (element.operationToken != KtTokens.PLUS) return false
        return element.containNoNewLine()
    }
}