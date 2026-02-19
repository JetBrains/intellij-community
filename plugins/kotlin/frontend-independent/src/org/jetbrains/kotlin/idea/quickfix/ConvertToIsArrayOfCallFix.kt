// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

class ConvertToIsArrayOfCallFix(
    element: KtIsExpression,
    private val lhsIsNullable: Boolean,
    private val lhsIsArray: Boolean,
    private val arrayArgumentTypeText: String
): PsiUpdateModCommandAction<KtIsExpression>(element) {

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("fix.convert.to.is.array.of.call")

    override fun invoke(
        context: ActionContext,
        element: KtIsExpression,
        updater: ModPsiUpdater
    ) {
        val isArrayOfCall = "isArrayOf<$arrayArgumentTypeText>()"
        val newPattern = when {
            lhsIsArray && !lhsIsNullable ->
                if (element.isNegated) "!$0.$isArrayOfCall" else "$0.$isArrayOfCall"
            lhsIsArray && lhsIsNullable ->
                if (element.isNegated) "$0?.$isArrayOfCall != true" else "$0?.$isArrayOfCall == true"
            else ->
                if (element.isNegated) "!($0 is Array<*> && $0.$isArrayOfCall)" else "$0 is Array<*> && $0.$isArrayOfCall"
        }
        val replaced = element.replaced(KtPsiFactory(context.project).createExpressionByPattern(newPattern, element.leftHandSide))
        ShortenReferencesFacility.getInstance().shorten(replaced)
    }
}