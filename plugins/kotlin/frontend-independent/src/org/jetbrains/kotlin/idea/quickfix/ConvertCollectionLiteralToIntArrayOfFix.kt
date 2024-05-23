// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

class ConvertCollectionLiteralToIntArrayOfFix(
    element: KtCollectionLiteralExpression,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtCollectionLiteralExpression, Unit>(element, Unit) {

    override fun invoke(
        actionContext: ActionContext,
        element: KtCollectionLiteralExpression,
        elementContext: Unit,
        updater: ModPsiUpdater
    ) {
        element.text.takeIf { it.first() == '[' && it.last() == ']' }?.drop(1)?.dropLast(1)?.let { content ->
            val psiFactory = KtPsiFactory(actionContext.project)
            element.replace(psiFactory.createExpressionByPattern("intArrayOf($0)", content))
        }
    }

    override fun getFamilyName(): String = KotlinBundle.message("replace.with.arrayof")
}
