// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.search

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.search.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.Companion.scriptDefinitionExists
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.idea.base.utils.fqname.getKotlinFqName as getKotlinFqNameOriginal

infix fun SearchScope.and(otherScope: SearchScope): SearchScope = intersectWith(otherScope)
infix fun SearchScope.or(otherScope: SearchScope): SearchScope = union(otherScope)
infix fun GlobalSearchScope.or(otherScope: SearchScope): GlobalSearchScope = union(otherScope)
operator fun SearchScope.minus(otherScope: GlobalSearchScope): SearchScope = this and !otherScope
operator fun GlobalSearchScope.not(): GlobalSearchScope = GlobalSearchScope.notScope(this)

fun SearchScope.unionSafe(other: SearchScope): SearchScope {
    if (this is LocalSearchScope && this.scope.isEmpty()) {
        return other
    }
    if (other is LocalSearchScope && other.scope.isEmpty()) {
        return this
    }
    return this.union(other)
}

fun Project.allScope(): GlobalSearchScope = GlobalSearchScope.allScope(this)

fun Project.projectScope(): GlobalSearchScope = GlobalSearchScope.projectScope(this)

fun PsiFile.fileScope(): GlobalSearchScope = GlobalSearchScope.fileScope(this)

fun Project.containsKotlinFile(): Boolean = FileTypeIndex.containsFileOfType(KotlinFileType.INSTANCE, projectScope())

fun GlobalSearchScope.restrictByFileType(fileType: FileType) = GlobalSearchScope.getScopeRestrictedByFileTypes(this, fileType)

fun SearchScope.restrictByFileType(fileType: FileType): SearchScope = when (this) {
    is GlobalSearchScope -> restrictByFileType(fileType)
    is LocalSearchScope -> {
        val elements = scope.filter { it.containingFile.fileType == fileType }
        when (elements.size) {
            0 -> GlobalSearchScope.EMPTY_SCOPE
            scope.size -> this
            else -> LocalSearchScope(elements.toTypedArray())
        }
    }
    else -> this
}

fun GlobalSearchScope.restrictToKotlinSources() = restrictByFileType(KotlinFileType.INSTANCE)

fun SearchScope.restrictToKotlinSources() = restrictByFileType(KotlinFileType.INSTANCE)

fun SearchScope.excludeKotlinSources(): SearchScope = excludeFileTypes(KotlinFileType.INSTANCE)

fun SearchScope.excludeFileTypes(vararg fileTypes: FileType): SearchScope {
    return if (this is GlobalSearchScope) {
        val includedFileTypes = FileTypeRegistry.getInstance().registeredFileTypes.filter { it !in fileTypes }.toTypedArray()
        GlobalSearchScope.getScopeRestrictedByFileTypes(this, *includedFileTypes)
    } else {
        this as LocalSearchScope
        val filteredElements = scope.filter { it.containingFile.fileType !in fileTypes }
        if (filteredElements.isNotEmpty())
            LocalSearchScope(filteredElements.toTypedArray())
        else
            GlobalSearchScope.EMPTY_SCOPE
    }
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
fun PsiElement.useScope():SearchScope = PsiSearchHelper.getInstance(project).getUseScope(this)
fun PsiElement.codeUsageScope(): SearchScope = PsiSearchHelper.getInstance(project).getCodeUsageScope(this)
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

fun findScriptsWithUsages(declaration: KtNamedDeclaration, processor:(KtFile) -> Boolean): Boolean {
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


data class ReceiverTypeSearcherInfo(
    val psiClass: PsiClass?,
    val containsTypeOrDerivedInside: ((KtDeclaration) -> Boolean)
)

fun PsiReference.isImportUsage(): Boolean =
    element.getNonStrictParentOfType<KtImportDirective>() != null

// Used in the "mirai" plugin
@Deprecated(
    "Use org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName()",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("getKotlinFqName()", "org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName")
)
fun PsiElement.getKotlinFqName(): FqName? = getKotlinFqNameOriginal()

fun PsiElement?.isPotentiallyOperator(): Boolean {
    val namedFunction = safeAs<KtNamedFunction>() ?: return false
    if (namedFunction.hasModifier(KtTokens.OPERATOR_KEYWORD)) return true
    // operator modifier could be omitted for overriding function
    if (!namedFunction.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return false
    val name = namedFunction.name ?: return false
    if (!OperatorConventions.isConventionName(Name.identifier(name))) return false

    // TODO: it's fast PSI-based check, a proper check requires call to resolveDeclarationWithParents() that is not frontend-independent
    return true
}