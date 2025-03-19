// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.search

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.idea.base.util.codeUsageScope
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.base.util.restrictToKotlinSources
import org.jetbrains.kotlin.idea.base.util.useScope
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.types.expressions.OperatorConventions

@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.util.allScope' instead",
    ReplaceWith("this.allScope()", imports = ["org.jetbrains.kotlin.idea.base.util.allScope"]),
    DeprecationLevel.ERROR
)
fun Project.allScope(): GlobalSearchScope = GlobalSearchScope.allScope(this)

@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.util.projectScope' instead",
    ReplaceWith("this.projectScope()", imports = ["org.jetbrains.kotlin.idea.base.util.projectScope"]),
    DeprecationLevel.ERROR
)
fun Project.projectScope(): GlobalSearchScope = GlobalSearchScope.projectScope(this)

/**
 * `( *\\( *)` and `( *\\) *)` – to find parenthesis
 * `( *, *(?![^\\[]*]))` – to find commas outside square brackets
 */
private val parenthesisRegex = Regex("( *\\( *)|( *\\) *)|( *, *(?![^\\[]*]))")

private inline fun CharSequence.ifNotEmpty(action: (CharSequence) -> Unit) {
    takeIf(CharSequence::isNotBlank)?.let(action)
}

fun SearchScope.toHumanReadableString(): String = buildString {
    val scopeText = this@toHumanReadableString.toString()
    var currentIndent = 0
    var lastIndex = 0
    for (parenthesis in parenthesisRegex.findAll(scopeText)) {
        val subSequence = scopeText.subSequence(lastIndex, parenthesis.range.first)
        subSequence.ifNotEmpty {
            append(" ".repeat(currentIndent))
            appendLine(it)
        }

        val value = parenthesis.value
        when {
            "(" in value -> currentIndent += 2
            ")" in value -> currentIndent -= 2
        }

        lastIndex = parenthesis.range.last + 1
    }

    if (isEmpty()) append(scopeText)
}

// Copied from SearchParameters.getEffectiveSearchScope()
fun ReferencesSearch.SearchParameters.effectiveSearchScope(element: PsiElement): SearchScope {
    if (element == elementToSearch) return effectiveSearchScope
    if (isIgnoreAccessScope) return scopeDeterminedByUser
    val accessScope = element.useScope()
    return scopeDeterminedByUser.intersectWith(accessScope)
}

fun isOnlyKotlinSearch(searchScope: SearchScope): Boolean {
    return searchScope is LocalSearchScope && searchScope.scope.all { it.containingFile is KtFile }
}

fun PsiElement.codeUsageScopeRestrictedToProject(): SearchScope = project.projectScope().intersectWith(codeUsageScope())

// TODO: improve scope calculations
fun PsiElement.codeUsageScopeRestrictedToKotlinSources(): SearchScope = codeUsageScope().restrictToKotlinSources()

fun PsiSearchHelper.isCheapEnoughToSearchConsideringOperators(
    name: String,
    scope: GlobalSearchScope,
    fileToIgnoreOccurrencesIn: PsiFile? = null
): PsiSearchHelper.SearchCostResult {
    if (OperatorConventions.isConventionName(Name.identifier(name))) {
        return PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES
    }

    return isCheapEnoughToSearch(name, scope, fileToIgnoreOccurrencesIn)
}

fun PsiReference.isImportUsage(): Boolean =
    element.getNonStrictParentOfType<KtImportDirective>() != null

