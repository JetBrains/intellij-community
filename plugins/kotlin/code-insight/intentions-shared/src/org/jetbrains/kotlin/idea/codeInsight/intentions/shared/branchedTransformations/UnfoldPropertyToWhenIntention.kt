// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared.branchedTransformations

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.intentions.shared.branchedTransformations.UnfoldPropertyUtils.prepareUnfoldPropertyContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.BranchedUnfoldingUtils
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtWhenExpression

class UnfoldPropertyToWhenIntention: KotlinApplicableModCommandAction<KtProperty, UnfoldPropertyUtils.Context>(KtProperty::class) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtProperty,
        elementContext: UnfoldPropertyUtils.Context,
        updater: ModPsiUpdater
    ) {
        val assignment = UnfoldPropertyUtils.splitPropertyDeclaration(element, elementContext.propertyExplicitType) ?: return
        BranchedUnfoldingUtils.unfoldAssignmentToWhen(assignment) { updater.moveCaretTo(it) }
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.property.initializer.with.when.expression")

    override fun isApplicableByPsi(element: KtProperty): Boolean {
        if (!element.isLocal) return false
        val initializer = element.initializer as? KtWhenExpression ?: return false
        if (!KtPsiUtil.checkWhenExpressionHasSingleElse(initializer)) return false
        return initializer.entries.none { it.expression == null }
    }

    override fun KaSession.prepareContext(element: KtProperty): UnfoldPropertyUtils.Context? =
        prepareUnfoldPropertyContext(element)
}