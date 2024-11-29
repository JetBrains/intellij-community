// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.diagnostics.WhenMissingCase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddRemainingWhenBranchesUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddRemainingWhenBranchesUtils.addRemainingWhenBranches
import org.jetbrains.kotlin.psi.KtWhenExpression

internal class AddWhenRemainingBranchesIntention :
    KotlinApplicableModCommandAction<KtWhenExpression, AddRemainingWhenBranchesUtils.ElementContext>(KtWhenExpression::class) {

    override fun getFamilyName(): String = AddRemainingWhenBranchesUtils.familyAndActionName(false)

    override fun isApplicableByPsi(element: KtWhenExpression): Boolean = true

    context(KaSession)
    override fun prepareContext(element: KtWhenExpression): AddRemainingWhenBranchesUtils.ElementContext? {
        val whenMissingCases = element.computeMissingCases().takeIf {
            it.isNotEmpty() && it.singleOrNull() != WhenMissingCase.Unknown
        } ?: return null
        return AddRemainingWhenBranchesUtils.ElementContext(whenMissingCases, enumToStarImport = null)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtWhenExpression,
        elementContext: AddRemainingWhenBranchesUtils.ElementContext,
        updater: ModPsiUpdater,
    ) {
        addRemainingWhenBranches(element, elementContext)
    }
}