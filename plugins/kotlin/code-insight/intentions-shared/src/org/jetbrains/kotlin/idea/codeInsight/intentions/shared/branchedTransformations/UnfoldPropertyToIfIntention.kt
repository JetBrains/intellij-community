// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared.branchedTransformations

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.branchedTransformations.UnfoldPropertyUtils.prepareUnfoldPropertyContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.BranchedUnfoldingUtils
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtProperty

internal class UnfoldPropertyToIfIntention :
    KotlinApplicableModCommandAction<KtProperty, UnfoldPropertyUtils.Context>(KtProperty::class) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.property.initializer.with.if.expression")

    override fun getPresentation(context: ActionContext, element: KtProperty): Presentation =
        Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)

    override fun isApplicableByPsi(element: KtProperty): Boolean =
        element.isLocal && element.initializer is KtIfExpression

    override fun getApplicableRanges(element: KtProperty): List<TextRange> {
        val initializer = element.initializer as? KtIfExpression ?: return emptyList()
        val endOffset = initializer.ifKeyword.textRangeIn(element).endOffset
        return listOf(TextRange(0, endOffset))
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtProperty): UnfoldPropertyUtils.Context? =
        prepareUnfoldPropertyContext(element)

    override fun invoke(
        actionContext: ActionContext,
        element: KtProperty,
        elementContext: UnfoldPropertyUtils.Context,
        updater: ModPsiUpdater,
    ) {
        val assignment = UnfoldPropertyUtils.splitPropertyDeclaration(element, elementContext.propertyExplicitType) ?: return
        BranchedUnfoldingUtils.unfoldAssignmentToIf(assignment) { updater.moveCaretTo(it) }
    }
}
