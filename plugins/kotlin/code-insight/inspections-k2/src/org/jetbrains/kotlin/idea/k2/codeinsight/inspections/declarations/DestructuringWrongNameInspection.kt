// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.declarations

import com.intellij.codeInsight.daemon.impl.quickfix.RenameElementFix
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.extractPrimaryParameters
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.destructuringDeclarationVisitor

internal class DestructuringWrongNameInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return destructuringDeclarationVisitor { declaration -> processDestructuringDeclaration(holder, declaration) }
    }

    private fun processDestructuringDeclaration(holder: ProblemsHolder, declaration: KtDestructuringDeclaration) {
        val parameterNames = analyze(declaration) {
            extractPrimaryParameters(declaration)?.map { it.name.asString() }
        } ?: return

        declaration.entries
            .asSequence()
            .withIndex()
            .filter { (index, variable) -> variable.name != parameterNames.getOrNull(index) && variable.name in parameterNames }
            .forEach { (index, variable) ->
                // 'variable' can't be null because of the filter above ('parameterNames' only contains non-nulls)
                val message = KotlinBundle.message("variable.name.0.matches.the.name.of.a.different.component", variable.name!!)
                if (index < parameterNames.size) {
                    val parameterName = parameterNames[index]
                    val fix = RenameElementFix(variable, parameterName)
                    holder.registerProblem(variable, message, fix)
                } else {
                    holder.registerProblem(variable, message)
                }
            }
    }
}
