// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.declarations

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.destructuringDeclarationVisitor

internal class RedundantDestructuringRenameInspection : AbstractKotlinInspection() {

    override fun isAvailableForFile(file: PsiFile): Boolean =
        file.languageVersionSettings.supportsFeature(LanguageFeature.NameBasedDestructuring)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = destructuringDeclarationVisitor { declaration ->
        if (declaration.hasSquareBrackets()) return@destructuringDeclarationVisitor

        val isShortFormRenameAvailable =
            declaration.languageVersionSettings.supportsFeature(LanguageFeature.EnableNameBasedDestructuringShortForm)

        for (entry in declaration.entries) {
            val initializer = entry.initializer ?: continue
            if (entry.ownValOrVarKeyword == null && !isShortFormRenameAvailable) continue

            val entryName = entry.nameAsName ?: continue
            if (entryName.isSpecial || entryName != initializer.getReferencedNameAsName()) continue

            holder.registerProblem(
                initializer,
                KotlinBundle.message("inspection.redundant.destructuring.rename"),
                ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                RemoveRedundantDestructuringRenameFix()
            )
        }
    }
}

private class RemoveRedundantDestructuringRenameFix : PsiUpdateModCommandQuickFix() {

    override fun getFamilyName(): @IntentionName String = KotlinBundle.message("remove.redundant.destructuring.rename")

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val initializer = element as? KtNameReferenceExpression ?: return
        val entry = initializer.parent as? KtDestructuringDeclarationEntry ?: return
        val replacementEntry = createReplacementEntry(project, entry) ?: return
        entry.replace(replacementEntry)
    }

    private fun createReplacementEntry(project: Project, entry: KtDestructuringDeclarationEntry): KtDestructuringDeclarationEntry? {
        val name = entry.name ?: return null
        val keywordPrefix = entry.ownValOrVarKeyword?.let { "${it.text} " }.orEmpty()
        val typeSuffix = entry.typeReference?.let { ": ${it.text}" }.orEmpty()
        val replacementText = "$keywordPrefix$name$typeSuffix"
        val declarationText = if (entry.ownValOrVarKeyword != null) {
            "($replacementText) = null"
        } else {
            "val ($replacementText) = null"
        }

        val psiFactory = KtPsiFactory(project)
        val declaration = psiFactory.createDestructuringDeclaration(declarationText)
        return declaration.entries.singleOrNull()
    }
}
