// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.search

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.util.*
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.Companion.scriptDefinitionExists
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.types.expressions.OperatorConventions

@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.util.minus' instead",
    ReplaceWith("this.minus", imports = ["org.jetbrains.kotlin.idea.base.util.minus"]),
    DeprecationLevel.ERROR
)
operator fun SearchScope.minus(otherScope: GlobalSearchScope): SearchScope = this and !otherScope

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

@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.util.moduleScope' instead",
    ReplaceWith("this.moduleScope()", imports = ["org.jetbrains.kotlin.idea.base.util.moduleScope"]),
    DeprecationLevel.ERROR
)
fun PsiFile.fileScope(): GlobalSearchScope = GlobalSearchScope.fileScope(this)

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
    fileToIgnoreOccurrencesIn: PsiFile?,
    progress: ProgressIndicator?
): PsiSearchHelper.SearchCostResult {
    if (OperatorConventions.isConventionName(Name.identifier(name))) {
        return PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES
    }

    return isCheapEnoughToSearch(name, scope, fileToIgnoreOccurrencesIn, progress)
}

fun findScriptsWithUsages(declaration: KtNamedDeclaration, processor: (KtFile) -> Boolean): Boolean {
    val project = declaration.project
    val scope = declaration.useScope() as? GlobalSearchScope ?: return true

    val name = declaration.name.takeIf { it?.isNotBlank() == true } ?: return true
    val collector = Processor<VirtualFile> { file ->
        val ktFile =
            (PsiManager.getInstance(project).findFile(file) as? KtFile)?.takeIf { it.scriptDefinitionExists() } ?: return@Processor true
        processor(ktFile)
    }
    return FileBasedIndex.getInstance().getFilesWithKey(
        IdIndex.NAME,
        setOf(IdIndexEntry(name, true)),
        collector,
        scope
    )
}

fun PsiReference.isImportUsage(): Boolean =
    element.getNonStrictParentOfType<KtImportDirective>() != null

// Used in the "mirai" plugin
@Deprecated(
    "Use org.jetbrains.kotlin.idea.base.psi.kotlinFqName",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("kotlinFqName", "org.jetbrains.kotlin.idea.base.psi.kotlinFqName")
)
fun PsiElement.getKotlinFqName(): FqName? = kotlinFqName