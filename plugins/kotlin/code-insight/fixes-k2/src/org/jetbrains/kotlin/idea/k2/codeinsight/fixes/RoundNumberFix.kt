// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.endOffset

internal class RoundNumberFix : PsiUpdateModCommandAction<KtExpression>, LowPriorityAction {
    private val roundFunction: String

    constructor(element: KtExpression, expectedType: String) : super(element) {
        roundFunction = "roundTo$expectedType"
    }

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("round.using.0", roundFunction)

    override fun invoke(
        context: ActionContext,
        element: KtExpression,
        updater: ModPsiUpdater
    ) {
        val file = element.containingKtFile
        val replaced = element.replaced(KtPsiFactory(context.project).createExpressionByPattern("$0.$roundFunction()", element))
        file.addImport(FqName("kotlin.math.$roundFunction"))
        updater.moveCaretTo(replaced.endOffset)
    }
}
