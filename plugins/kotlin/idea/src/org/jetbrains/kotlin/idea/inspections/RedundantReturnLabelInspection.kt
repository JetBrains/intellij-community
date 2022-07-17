// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.quickfix.RemoveReturnLabelFix
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.returnExpressionVisitor

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

class RedundantReturnLabelInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = returnExpressionVisitor(
        fun(returnExpression) {
            val label = returnExpression.getTargetLabel() ?: return
            val function = returnExpression.getParentOfType<KtNamedFunction>(true, KtLambdaExpression::class.java) ?: return
            if (function.name == null) return
            val labelName = label.getReferencedName()
            holder.registerProblem(
                label,
                KotlinBundle.message("redundant.0", labelName),
                IntentionWrapper(RemoveReturnLabelFix(returnExpression, labelName)),
            )
        },
    )
}
