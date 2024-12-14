// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.ConstantConditionIfUtils.getConditionConstantValueIfAny
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ConstantConditionIfFix
import org.jetbrains.kotlin.psi.ifExpressionVisitor

internal class ConstantConditionIfInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return ifExpressionVisitor { expression ->
            val constantValue = expression.getConditionConstantValueIfAny() ?: return@ifExpressionVisitor
            val fixes = ConstantConditionIfFix.collectFixes(expression, constantValue)
            holder.registerProblem(
              expression,
              expression.condition?.textRangeIn(expression),
              KotlinBundle.message("condition.is.always.0", constantValue),
              *fixes.toTypedArray()
            )
        }
    }
}
