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
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.destructuringDeclarationVisitor

internal class DestructuringWrongNameInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return destructuringDeclarationVisitor { declaration -> processDestructuringDeclaration(holder, declaration) }
    }

    private fun processDestructuringDeclaration(holder: ProblemsHolder, declaration: KtDestructuringDeclaration) {
        if (declaration.isFullForm) return // skip name-based destructuring

        val context = analyze(declaration) {
            val parameters = extractPrimaryParameters(declaration) ?: return@analyze null
            val allParameterNames = parameters.mapTo(mutableSetOf()) { it.name.asString() }
            val entryInfos = declaration.entries.mapIndexed { index, entry ->
                val param = parameters.getOrNull(index)
                val expectedName = param?.name?.asString()
                val entryType = entry.typeReference?.type
                val canSuggestRename = expectedName != null && (entryType == null || param.returnType.isSubtypeOf(entryType))
                EntryInfo(entry, expectedName, canSuggestRename)
            }
            DestructuringContext(entryInfos, allParameterNames)
        } ?: return

        for (info in context.entryInfos) {
            val variableName = info.entry.name ?: continue
            if (variableName == info.expectedName) continue
            if (variableName !in context.allParameterNames) continue

            val message = KotlinBundle.message("variable.name.0.matches.the.name.of.a.different.component", variableName)
            if (info.expectedName != null && info.canSuggestRename) {
                holder.registerProblem(info.entry, message, RenameElementFix(info.entry, info.expectedName))
            } else {
                holder.registerProblem(info.entry, message)
            }
        }
    }

    private data class EntryInfo(
        val entry: KtDestructuringDeclarationEntry,
        val expectedName: String?,
        val canSuggestRename: Boolean,
    )

    private data class DestructuringContext(
        val entryInfos: List<EntryInfo>,
        val allParameterNames: Set<String>,
    )
}
