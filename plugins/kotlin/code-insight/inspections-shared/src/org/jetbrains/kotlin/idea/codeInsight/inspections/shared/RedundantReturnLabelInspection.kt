// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.RemoveReturnLabelFix
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

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