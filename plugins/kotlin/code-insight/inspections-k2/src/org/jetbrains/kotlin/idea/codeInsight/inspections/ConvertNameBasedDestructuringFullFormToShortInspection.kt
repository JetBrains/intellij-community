// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.ConvertNameBasedDestructuringFullFormToShortInspection.Context
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.extractDestructuringComponentNames
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.destructuringDeclarationVisitor

internal class ConvertNameBasedDestructuringFullFormToShortInspection :
    KotlinApplicableInspectionBase.Simple<KtDestructuringDeclaration, Context>() {

    data class Context(val isVar: Boolean)

    override fun isAvailableForFile(file: PsiFile): Boolean {
        return super.isAvailableForFile(file) &&
                file.languageVersionSettings.supportsFeature(LanguageFeature.NameBasedDestructuring) &&
                file.languageVersionSettings.supportsFeature(LanguageFeature.DeprecateNameMismatchInShortDestructuringWithParentheses)
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> = destructuringDeclarationVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtDestructuringDeclaration): Boolean {
        if (!element.isFullForm || element.lPar == null || element.rPar == null) return false
        if (element.initializer == null && element.parent !is KtParameter && element.parent !is KtForExpression) return false

        val supportsRenames = element.languageVersionSettings.supportsFeature(LanguageFeature.EnableNameBasedDestructuringShortForm)
        return supportsRenames || element.entries.none { it.initializer != null }
    }

    override fun getApplicableRanges(element: KtDestructuringDeclaration): List<TextRange> =
        ApplicabilityRange.single(element) { it.entries.firstOrNull()?.ownValOrVarKeyword }

    override fun getProblemDescription(element: KtDestructuringDeclaration, context: Context): String {
        return KotlinBundle.message("inspection.full.form.to.short.problem.description")
    }

    override fun createQuickFix(element: KtDestructuringDeclaration, context: Context): KotlinModCommandQuickFix<KtDestructuringDeclaration> {
        return ConvertNameBasedDestructuringFullFormToShortFix(context)
    }

    context(session: KaSession)
    override fun prepareContext(element: KtDestructuringDeclaration): Context? {
        val keywordType = element.commonEntryKeywordType() ?: return null

        if (!element.languageVersionSettings.supportsFeature(LanguageFeature.EnableNameBasedDestructuringShortForm)) {
            val componentNames = session.extractDestructuringComponentNames(element) ?: return null
            if (element.entries.map { it.nameAsName } != componentNames) return null
        }

        return Context(isVar = keywordType == KtTokens.VAR_KEYWORD)
    }
}

private fun KtDestructuringDeclaration.commonEntryKeywordType(): IElementType? {
    val keywordType = entries.firstOrNull()?.ownValOrVarKeyword?.node?.elementType ?: return null
    return keywordType.takeIf { entries.all { it.ownValOrVarKeyword?.node?.elementType == keywordType } }
}

private class ConvertNameBasedDestructuringFullFormToShortFix(private val context: Context) :
    KotlinModCommandQuickFix<KtDestructuringDeclaration>() {

    override fun getFamilyName(): String = KotlinBundle.message("inspection.full.form.to.short.fix.text")

    override fun applyFix(project: Project, element: KtDestructuringDeclaration, updater: ModPsiUpdater) {
        val entryKeywords = element.entries.map { it.ownValOrVarKeyword ?: return }

        if (element.initializer != null) {
            val lPar = element.lPar ?: return
            val psiFactory = KtPsiFactory(project)
            val outerKeyword = if (context.isVar) psiFactory.createVarKeyword() else psiFactory.createValKeyword()
            element.addBefore(outerKeyword, lPar)
            element.addBefore(psiFactory.createWhiteSpace(), lPar)
        }

        for (keyword in entryKeywords) {
            (keyword.nextSibling as? PsiWhiteSpace)?.delete()
            keyword.delete()
        }
    }
}
