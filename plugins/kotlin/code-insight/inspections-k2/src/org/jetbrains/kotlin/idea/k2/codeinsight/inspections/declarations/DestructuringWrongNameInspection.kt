// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.declarations

import com.intellij.codeInsight.daemon.impl.quickfix.RenameElementFix
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.types.KtFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.destructuringDeclarationVisitor

class DestructuringWrongNameInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return destructuringDeclarationVisitor { declaration -> processDestructuringDeclaration(holder, declaration) }
    }

    private fun processDestructuringDeclaration(holder: ProblemsHolder, declaration: KtDestructuringDeclaration) {
        analyze(declaration) {
            val type = getClassType(declaration) ?: return
            if (type.nullability != KtTypeNullability.NON_NULLABLE) return
            val classSymbol = type.expandedClassSymbol

            if (classSymbol is KtNamedClassOrObjectSymbol && classSymbol.isData) {
                val constructorSymbol = classSymbol.getDeclaredMemberScope()
                    .getConstructors()
                    .find { it.isPrimary }
                    ?: return

                val parameterNames = constructorSymbol.valueParameters.map { it.name.asString() }

                declaration.entries
                    .asSequence()
                    .withIndex()
                    .filter { (index, variable) -> variable.name != parameterNames.getOrNull(index) && variable.name in parameterNames }
                    .forEach { (index, variable) ->
                        // 'variable' can't be null because of the filter above ('parameterNames' only contains non-nulls)
                        val message = KotlinBundle.message("variable.name.0.matches.the.name.of.a.different.component", variable.name!!)
                        if (index < parameterNames.size) {
                            val parameter = constructorSymbol.valueParameters[index]
                            val fix = RenameElementFix(variable, parameter.name.asString())
                            holder.registerProblem(variable, message, fix)
                        } else {
                            holder.registerProblem(variable, message)
                        }
                    }
            }
        }
    }

    private fun KtAnalysisSession.getClassType(declaration: KtDestructuringDeclaration): KtNonErrorClassType? {
        val initializer = declaration.initializer
        val parentAsParameter = declaration.parent as? KtParameter
        val type = when {
            initializer != null -> initializer.getKtType()
            parentAsParameter != null -> parentAsParameter.getParameterSymbol().returnType
            else -> null
        }
        return when (type) {
            is KtNonErrorClassType -> type
            is KtFlexibleType -> type.lowerBound as? KtNonErrorClassType
            else -> null
        }
    }
}
