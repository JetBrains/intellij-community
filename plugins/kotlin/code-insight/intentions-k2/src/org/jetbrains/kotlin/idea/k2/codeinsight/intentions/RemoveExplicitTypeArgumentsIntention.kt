// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.RemoveExplicitTypeArgumentsUtils
import org.jetbrains.kotlin.idea.k2.refactoring.util.areTypeArgumentsRedundant
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtTypeArgumentList

internal class RemoveExplicitTypeArgumentsIntention :
    KotlinApplicableModCommandAction<KtTypeArgumentList, Unit>(KtTypeArgumentList::class) {

    override fun getFamilyName(): String = KotlinBundle.message("remove.explicit.type.arguments")

    override fun isApplicableByPsi(element: KtTypeArgumentList): Boolean {
        val callExpression = element.parent as? KtCallExpression ?: return false
        return RemoveExplicitTypeArgumentsUtils.isApplicableByPsi(callExpression)
    }

    context(KaSession)
    override fun prepareContext(element: KtTypeArgumentList): Unit? {
        return if (areTypeArgumentsRedundant(element)) Unit else null
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtTypeArgumentList,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        RemoveExplicitTypeArgumentsUtils.applyTo(element)
    }
}