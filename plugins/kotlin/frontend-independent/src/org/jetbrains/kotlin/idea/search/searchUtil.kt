// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.search

import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.fileTypes.FileType
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
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport.Companion.scriptDefinitionExists
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.java.propertyNameByGetMethodName
import org.jetbrains.kotlin.load.java.propertyNamesBySetMethodName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember
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
            0 -> LocalSearchScope.EMPTY
            scope.size -> this
            else -> LocalSearchScope(elements.toTypedArray())
        }
    }
    else -> this
}

fun GlobalSearchScope.restrictToKotlinSources() = restrictByFileType(KotlinFileType.INSTANCE)

fun SearchScope.restrictToKotlinSources() = restrictByFileType(KotlinFileType.INSTANCE)

fun SearchScope.excludeKotlinSources(project: Project): SearchScope = excludeFileTypes(project, KotlinFileType.INSTANCE)

fun Project.everythingScopeExcludeFileTypes(vararg fileTypes: FileType): GlobalSearchScope {
    return GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.everythingScope(this), *fileTypes).not()
}

fun SearchScope.excludeFileTypes(project: Project, vararg fileTypes: FileType): SearchScope {
    return if (this is GlobalSearchScope) {
        this.intersectWith(project.everythingScopeExcludeFileTypes(*fileTypes))
    } else {
        this as LocalSearchScope
        val filteredElements = scope.filter { it.containingFile.fileType !in fileTypes }
        if (filteredElements.isNotEmpty())
            LocalSearchScope(filteredElements.toTypedArray())
        else
            LocalSearchScope.EMPTY
    }
}

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
fun PsiElement.useScope(): SearchScope = PsiSearchHelper.getInstance(project).getUseScope(this)
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

private val PsiMethod.canBeGetter: Boolean
    get() = JvmAbi.isGetterName(name) && parameters.isEmpty() && returnTypeElement?.textMatches("void") != true

private val PsiMethod.canBeSetter: Boolean
    get() = JvmAbi.isSetterName(name) && parameters.size == 1 && returnTypeElement?.textMatches("void") != false

private val PsiMethod.probablyCanHaveSyntheticAccessors: Boolean
    get() = canHaveOverride && !hasTypeParameters() && !isFinalProperty

private val PsiMethod.getterName: Name? get() = propertyNameByGetMethodName(Name.identifier(name))
private val PsiMethod.setterNames: Collection<Name>? get() = propertyNamesBySetMethodName(Name.identifier(name)).takeIf { it.isNotEmpty() }

private val PsiMethod.isFinalProperty: Boolean
    get() {
        val property = unwrapped as? KtProperty ?: return false
        if (property.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return false
        val containingClassOrObject = property.containingClassOrObject ?: return true
        return containingClassOrObject is KtObjectDeclaration
    }

private val PsiMethod.isTopLevelDeclaration: Boolean get() = unwrapped?.isTopLevelKtOrJavaMember() == true

val PsiMethod.syntheticAccessors: Collection<Name>
    get() {
        if (!probablyCanHaveSyntheticAccessors) return emptyList()

        return when {
            canBeGetter -> listOfNotNull(getterName)
            canBeSetter -> setterNames.orEmpty()
            else -> emptyList()
        }
    }

val PsiMethod.canHaveSyntheticAccessors: Boolean get() = probablyCanHaveSyntheticAccessors && (canBeGetter || canBeSetter)

val PsiMethod.canHaveSyntheticGetter: Boolean get() = probablyCanHaveSyntheticAccessors && canBeGetter

val PsiMethod.canHaveSyntheticSetter: Boolean get() = probablyCanHaveSyntheticAccessors && canBeSetter

val PsiMethod.syntheticGetter: Name? get() = if (canHaveSyntheticGetter) getterName else null

val PsiMethod.syntheticSetters: Collection<Name>? get() = if (canHaveSyntheticSetter) setterNames else null

/**
 * Attention: only language constructs are checked. For example: static member, constructor, top-level property
 * @return `false` if constraints are found. Otherwise, `true`
 */
val PsiMethod.canHaveOverride: Boolean get() = !hasModifier(JvmModifier.STATIC) && !isConstructor && !isTopLevelDeclaration
