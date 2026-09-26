// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Quick-fix for adding a new line after annotations in annotated expressions.
 */
class AddNewLineAfterAnnotationsFix(element: KtAnnotatedExpression) : KotlinPsiUpdateModCommandAction.ElementContextless<KtAnnotatedExpression>(element) {

    override fun getFamilyName(): String = KotlinBundle.message("fix.add.new.line.after.annotations")

    override fun invoke(
        context: ActionContext,
        element: KtAnnotatedExpression,
        updater: ModPsiUpdater
    ) {
        val baseExpression = element.baseExpression ?: return

        val annotationsText = element.text.substring(0, baseExpression.startOffsetInParent)
        val newExpression = KtPsiFactory(context.project).createBlock(annotationsText + "\n" + baseExpression.text).statements[0]
        
        element.replace(newExpression)
    }
}