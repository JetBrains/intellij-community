// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.modcommand.ModShowConflicts
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.util.RefactoringUIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allOverriddenSymbolsWithSelf
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.refactoring.canRefactorElement
import java.util.Locale

@ApiStatus.Internal
object ConvertFunctionToPropertyAndViceVersaUtils {

    fun reportDeclarationConflict(
        conflicts: MutableMap<PsiElement, ModShowConflicts.Conflict>,
        declaration: PsiElement,
        message: (renderedDeclaration: String) -> String,
    ) {
        val message = message(RefactoringUIUtil.getDescription(declaration, true).capitalize())
        conflicts.add(declaration, message)
    }

    fun String.capitalize(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    fun MutableMap<PsiElement, ModShowConflicts.Conflict>.add(element: PsiElement, message: String) {
        getOrPut(element) { ModShowConflicts.Conflict(mutableListOf()) }.messages().add(message)
    }

    fun KaSession.isOverride(symbol: KaCallableSymbol): Boolean =
        symbol.allOverriddenSymbolsWithSelf.singleOrNull() != null

    fun addConflictIfCantRefactor(callable: PsiElement, conflicts: MutableMap<PsiElement, ModShowConflicts.Conflict>) {
        if (!callable.canRefactorElement()) {
            val renderedCallable = RefactoringUIUtil.getDescription(callable, /*includeParent =*/ true).capitalize()
            conflicts.add(callable, KotlinBundle.message("can.t.modify.0", renderedCallable))
        }
    }

    fun findReferencesToElement(callable: PsiElement): Collection<PsiReference>? {
        val module = callable.module ?: return null
        val scope = GlobalSearchScope.moduleWithDependentsScope(module)

        val query = ReferencesSearch.search(callable, scope)
        return if (IntentionPreviewUtils.isIntentionPreviewActive()) {
            listOfNotNull(query.findFirst())
        } else {
            query.findAll()
        }
    }
}
