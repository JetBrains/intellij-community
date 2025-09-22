// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle

class KotlinAvoidRepositoriesInBuildGradleInspectionVisitor(val holder: ProblemsHolder) : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
        analyze(expression) {
            val symbol = expression.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return
            if (symbol.callableId?.callableName?.asString() != "repositories") return
            if (symbol.callableId?.packageName != FqName("org.gradle.kotlin.dsl")) return
        }

        holder.registerProblem(
            expression,
            GradleInspectionBundle.message("inspection.message.avoid.repositories.in.build.gradle.descriptor")
        )
    }
}