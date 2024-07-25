// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

class ReplaceWithComparisonFix(
    element: KtIsExpression,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtIsExpression, Unit>(element, Unit) {
    private val comparison = if (element.isNegated) "!=" else "=="

    override fun getFamilyName(): String = KotlinBundle.message("replace.with.0", comparison)

    override fun invoke(
        actionContext: ActionContext,
        element: KtIsExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val leftHandSide = element.leftHandSide
        val typeReference = element.typeReference?.text ?: return
        val binaryExpression = KtPsiFactory(actionContext.project).createExpressionByPattern("$0 $comparison $1", leftHandSide, typeReference)
        element.replace(binaryExpression)
    }
}
