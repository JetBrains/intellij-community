// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern

object ChangeToLabeledReturnUtils {

    fun applyTo(
        project: Project,
        returnExpression: KtReturnExpression,
        labeledReturn: String,
    ) {
        val factory = KtPsiFactory(project)
        val returnedExpression = returnExpression.returnedExpression
        val newExpression = if (returnedExpression == null)
            factory.createExpression(labeledReturn)
        else
            factory.createExpressionByPattern("$0 $1", labeledReturn, returnedExpression)
        returnExpression.replace(newExpression)
    }
}