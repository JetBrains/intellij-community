// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.gradle.scripting.k2.fixes.ShowDuplicateElementsAction
import org.jetbrains.kotlin.idea.base.util.letIf
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle

class KotlinAvoidDuplicateRepositoriesInspectionVisitor(
    private val holder: ProblemsHolder
) : KtVisitorVoid() {
    override fun visitCallExpression(expression: KtCallExpression) {
        if (!expression.isGradleRepositoriesBlock()) return
        val repositoriesBlock = expression.getBlock() ?: return
        val repositories = repositoriesBlock.childrenOfType<KtCallExpression>().filter { it.isRepositoryDeclaration() }
        val duplicateGroups = repositories.groupBy { it.normalizedRepositoryKey() }.filterValues { it.size > 1 }
        duplicateGroups.forEach { (key, repositories) ->
            repositories.forEach { repository ->
                val duplicateRepositories = repositories - repository
                val duplicateName = key.letIf(key.length > 20) { it.take(20) + "..." }
                holder.problem(
                    repository,
                    GradleInspectionBundle.message("inspection.message.avoid.duplicate.repositories.descriptor", duplicateName)
                ).fix(ShowDuplicateElementsAction.forRepositories(duplicateName, duplicateRepositories))
                    .range(repository.calleeExpression?.textRangeInParent ?: repository.textRangeInParent)
                    .register()
            }
        }
    }

    private fun KtCallExpression.isRepositoryDeclaration(): Boolean = analyze(this) {
        val symbol = this@isRepositoryDeclaration.resolveToCall()?.singleFunctionCallOrNull()?.symbol ?: return@analyze false
        val returnClassId = symbol.returnType.symbol?.classId ?: return@analyze false
        return isInheritor(
            this@isRepositoryDeclaration,
            returnClassId,
            ARTIFACT_REPOSITORY_CLASS_ID
        )
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
        private val ARTIFACT_REPOSITORY_CLASS_ID = ClassId.fromString("org/gradle/api/artifacts/repositories/ArtifactRepository")
    }
}

