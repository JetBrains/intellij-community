// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.psi.dotQualifiedExpressionVisitor

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

class JavaCollectionsStaticMethodOnImmutableListInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return dotQualifiedExpressionVisitor(fun(expression) {
            val (methodName, firstArg) = JavaCollectionsStaticMethodInspection.Util.getTargetMethodOnImmutableList(expression) ?: return
            holder.registerProblem(
                expression.callExpression?.calleeExpression ?: expression,
                KotlinBundle.message("call.of.java.mutator.0.on.immutable.kotlin.collection.1", methodName, firstArg.text)
            )
        })
    }
}
