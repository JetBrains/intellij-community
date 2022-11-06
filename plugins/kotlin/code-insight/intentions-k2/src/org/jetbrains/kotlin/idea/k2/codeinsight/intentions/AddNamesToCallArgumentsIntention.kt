// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRanges
import org.jetbrains.kotlin.idea.codeinsight.utils.dereferenceValidKeys
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddArgumentNamesUtils.addArgumentNames
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddArgumentNamesUtils.associateArgumentNamesStartingAt
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgument

internal class AddNamesToCallArgumentsIntention :
    KotlinApplicableIntentionWithContext<KtCallElement, AddNamesToCallArgumentsIntention.Context>(KtCallElement::class) {

    class Context(val argumentNames: Map<SmartPsiElementPointer<KtValueArgument>, Name>)

    override fun getFamilyName(): String = KotlinBundle.message("add.names.to.call.arguments")
    override fun getActionName(element: KtCallElement, context: Context): String = familyName

    override fun getApplicabilityRange() = applicabilityRanges { element: KtCallElement ->
        // Note: Applicability range matches FE 1.0 (see AddNamesToCallArgumentsIntention).
        val calleeExpression = element.calleeExpression ?: return@applicabilityRanges emptyList()
        val calleeExpressionTextRange = calleeExpression.textRangeIn(element)
        val arguments = element.valueArguments
        if (arguments.size < 2) {
            listOf(calleeExpressionTextRange)
        } else {
            val firstArgument = arguments.firstOrNull() as? KtValueArgument ?: return@applicabilityRanges emptyList()
            val endOffset = firstArgument.textRangeIn(element).endOffset
            listOf(TextRange(calleeExpressionTextRange.startOffset, endOffset))
        }
    }

    override fun isApplicableByPsi(element: KtCallElement): Boolean =
        // Note: `KtCallElement.valueArgumentList` only includes arguments inside parentheses; it doesn't include a trailing lambda.
        element.valueArgumentList?.arguments?.any { !it.isNamed() } ?: false

    context(KtAnalysisSession)
    override fun prepareContext(element: KtCallElement): Context? =
        associateArgumentNamesStartingAt(element, null)?.let { Context(it) }

    override fun apply(element: KtCallElement, context: Context, project: Project, editor: Editor?) =
        addArgumentNames(context.argumentNames.dereferenceValidKeys())
}