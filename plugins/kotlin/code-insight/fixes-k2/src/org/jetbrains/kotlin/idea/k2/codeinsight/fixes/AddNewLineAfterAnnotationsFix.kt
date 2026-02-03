// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Quick-fix for adding a new line after annotations in annotated expressions.
 */
class AddNewLineAfterAnnotationsFix(element: KtAnnotatedExpression) : PsiUpdateModCommandAction<KtAnnotatedExpression>(element) {

    override fun getFamilyName(): String = KotlinBundle.message("fix.add.new.line.after.annotations")

    override fun invoke(
        actionContext: ActionContext,
        element: KtAnnotatedExpression,
        updater: ModPsiUpdater
    ) {
        val baseExpression = element.baseExpression ?: return

        val annotationsText = element.text.substring(0, baseExpression.startOffsetInParent)
        val newExpression = KtPsiFactory(actionContext.project).createBlock(annotationsText + "\n" + baseExpression.text).statements[0]
        
        element.replace(newExpression)
    }
}