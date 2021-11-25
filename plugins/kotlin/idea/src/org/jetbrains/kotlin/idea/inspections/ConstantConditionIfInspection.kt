// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.quickfix.SimplifyIfExpressionFix
import org.jetbrains.kotlin.idea.quickfix.SimplifyIfExpressionFix.Companion.getConditionConstantValueIfAny
import org.jetbrains.kotlin.psi.ifExpressionVisitor

class ConstantConditionIfInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return ifExpressionVisitor { expression ->
            val constantValue = expression.getConditionConstantValueIfAny() ?: return@ifExpressionVisitor
            val fix = SimplifyIfExpressionFix.createFix(expression, constantValue) ?: return@ifExpressionVisitor
            holder.registerProblem(
                expression.condition!!,
                KotlinBundle.message("condition.is.always.0", constantValue),
                IntentionWrapper(fix)
            )
        }
    }
}
