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
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PropertyUtil
import com.intellij.psi.util.PsiUtil
import org.jetbrains.kotlin.analysis.api.resolution.symbols
import org.jetbrains.kotlin.analysis.api.resolution.tryResolveSymbols
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaJavaFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaKotlinPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSyntheticJavaPropertySymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.EditCommaSeparatedListHelper
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.SearchUtils.isOverridable
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.destructuringDeclarationVisitor
import org.jetbrains.kotlin.resolve.calls.util.isSingleUnderscore

internal class RedundantDestructuringUnderscoreInspection : AbstractKotlinInspection() {

    override fun isAvailableForFile(file: PsiFile): Boolean =
        file.languageVersionSettings.supportsFeature(LanguageFeature.NameBasedDestructuring)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = destructuringDeclarationVisitor { declaration ->
        if (declaration.hasSquareBrackets()) return@destructuringDeclarationVisitor
        if (declaration.entries.size <= 1) return@destructuringDeclarationVisitor

        val parent = declaration.parent
        if (parent is KtForExpression || parent is KtParameter) return@destructuringDeclarationVisitor

        val isShortFormAvailable =
            declaration.languageVersionSettings.supportsFeature(LanguageFeature.EnableNameBasedDestructuringShortForm)

        val entriesWithSafety = declaration.entries
            .filter { it.isSingleUnderscore && (it.ownValOrVarKeyword != null || isShortFormAvailable) }
            .associateWith { isSafeToRemove(it) }
        val allowBatch = entriesWithSafety.count { it.value } < declaration.entries.size

        for ((entry, isSafe) in entriesWithSafety) {
            holder.registerProblem(
                entry,
                KotlinBundle.message("inspection.redundant.destructuring.underscore"),
                ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                RemoveRedundantDestructuringUnderscoreFix(allowBatch && isSafe, mayChangeSemantics = !isSafe)
            )
        }
    }

    private fun isSafeToRemove(entry: KtDestructuringDeclarationEntry): Boolean {
        entry.initializer ?: return true
        return analyze(entry) {
            val symbol = entry.tryResolveSymbols()?.symbols?.firstOrNull()
            when (symbol) {
                is KaSyntheticJavaPropertySymbol -> {
                    val getter = symbol.javaGetterSymbol.psi as? PsiMethod
                    getter != null && !PsiUtil.canBeOverridden(getter) && PropertyUtil.getFieldOfGetter(getter) != null
                }

                is KaPropertySymbol -> {
                    val isLateinit = symbol is KaKotlinPropertySymbol && symbol.isLateInit

                    val fieldAnnotations = symbol.backingFieldSymbol?.annotations
                    val isVolatile = fieldAnnotations?.contains(JvmStandardClassIds.VOLATILE_ANNOTATION_CLASS_ID) == true
                            || fieldAnnotations?.contains(StandardClassIds.Annotations.Volatile) == true
                    val isSynchronized = symbol.getter?.annotations?.contains(JvmStandardClassIds.SYNCHRONIZED_ANNOTATION_CLASS_ID) == true

                    val declaration = symbol.psi as? KtDeclaration ?: return@analyze false
                    !declaration.isOverridable() && !isLateinit && !isVolatile && !isSynchronized
                            && !symbol.isDelegated && symbol.getter?.isNotDefault != true
                }

                is KaJavaFieldSymbol -> true
                else -> false
            }
        }
    }
}

private class RemoveRedundantDestructuringUnderscoreFix(
    private val allowBatch: Boolean,
    private val mayChangeSemantics: Boolean
) : PsiUpdateModCommandQuickFix() {

    override fun getFamilyName(): @IntentionName String =
        KotlinBundle.message("remove.redundant.destructuring.underscore")

    override fun getName(): @IntentionName String {
        val suffix = if (mayChangeSemantics) KotlinBundle.message("quickfix.text.suffix.may.change.semantics") else ""
        return familyName + suffix
    }

    override fun availableInBatchMode(): Boolean = allowBatch

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val entry = element as? KtDestructuringDeclarationEntry ?: return
        EditCommaSeparatedListHelper.removeItem(entry)
    }
}
