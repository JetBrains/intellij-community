// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.childrenOfType
import com.intellij.util.asSafely
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.gradle.scripting.k2.fixes.ShowDuplicateElementsAction
import org.jetbrains.kotlin.idea.base.util.letIf
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.parentOrNull
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle

class KotlinAvoidDuplicateRepositoriesInspectionVisitor(
    private val holder: ProblemsHolder
) : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
        if (expression.calleeExpression?.text != "repositories") return
        analyze(expression) {
            val callableId = expression.resolveExpression().asSafely<KaCallableSymbol>()?.callableId ?: return
            if (callableId.asSingleFqName() !in REPOSITORIES_FQ_NAMES) return
        }
        val repositoriesBlock = expression.getBlock() ?: return
        val repositories = repositoriesBlock.childrenOfType<KtCallExpression>().filter { isRepositoryDeclaration(it) }
        val duplicateGroups = repositories.groupBy { it.normalizedRepositoryKey() }.filterValues { it.size > 1 }
        duplicateGroups.forEach { (key, repositories) ->
            repositories.forEach { repository ->
                val duplicateRepositories = repositories - repository
                val duplicateName = key.letIf(key.length > 20) { it.take(20) + "..." }
                holder.problem(
                    repository,
                    GradleInspectionBundle.message("inspection.message.avoid.duplicate.repositories.descriptor", duplicateName)
                ).fix(
                    ShowDuplicateElementsAction(
                        duplicateName,
                        duplicateRepositories,
                        GradleInspectionBundle.message("intention.family.name.show.duplicate.repositories"),
                        GradleInspectionBundle.message("intention.choose.action.name.select.duplicate.repository"),
                        GradleInspectionBundle.message("intention.family.name.navigate.to.duplicate.repository")
                    )
                ).range(repository.calleeExpression?.textRangeInParent ?: repository.textRangeInParent)
                    .register()
            }
        }
    }

    private fun isRepositoryDeclaration(expression: KtCallExpression): Boolean = analyze(expression) {
        val symbol = expression.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return@analyze false
        if (symbol.callableId?.asSingleFqName()?.parentOrNull() == FqName("org.gradle.api.artifacts.dsl.RepositoryHandler")) {
            return true
        } else {
            val returnType = symbol.returnType.symbol?.classId?.asSingleFqName() ?: return@analyze false
            returnType.startsWith(FqName("org.gradle.api.artifacts.repositories"))
        }
    }

    /**
     * Builds a stable textual key for a repository declaration that ignores whitespace and comments,
     * while preserving the exact text of literals and other significant tokens.
     */
    private fun PsiElement.normalizedRepositoryKey(): String {
        val sb = StringBuilder()
        this.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is PsiWhiteSpace, is PsiComment -> return // skip insignificant tokens entirely
                }
                // Append text only for leaf elements to avoid duplication
                if (element.firstChild == null) {
                    sb.append(element.text)
                }
                super.visitElement(element)
            }
        })
        return sb.toString()
    }

    companion object {
        private val REPOSITORIES_FQ_NAMES = setOf(
            FqName("org.gradle.kotlin.dsl.repositories"),
            FqName("org.gradle.plugin.management.PluginManagementSpec.repositories"),
            FqName("org.gradle.api.initialization.resolve.DependencyResolutionManagement.repositories")
        )
    }
}

