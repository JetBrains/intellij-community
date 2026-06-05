// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.endOffset

internal class AddContextParameterToExistingContextFix(
    surroundingCall: KtCallExpression,
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtCallExpression>(surroundingCall) {

    override fun invoke(context: ActionContext, element: KtCallExpression, updater: ModPsiUpdater) {
        val psiFactory = KtPsiFactory(context.project)
        val argList = element.valueArgumentList ?: return
        if (argList.arguments.isNotEmpty()) {
            val addedComma = argList.addBefore(psiFactory.createComma(), argList.rightParenthesis)
            updater.moveCaretTo(addedComma.endOffset)
        } else {
            val leftParen = argList.leftParenthesis ?: return
            updater.moveCaretTo(leftParen.endOffset)
        }
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("fix.add.argument.to.existing.context")
}