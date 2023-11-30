// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.util.SmartList
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.base.analysis.isExcludedFromAutoImport
import org.jetbrains.kotlin.idea.base.psi.isExpectDeclaration
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.yieldIfNotNull

class KtSymbolFromIndexProvider private constructor(
    private val useSiteFile: KtFile,
    private val scope: GlobalSearchScope,
) {
    private val project: Project = useSiteFile.project

    context(KtAnalysisSession)
    private fun useSiteFilter(element: PsiElement): Boolean {
        if (element.kotlinFqName?.isExcludedFromAutoImport(project, useSiteFile) == true) return false

        val isCommon = useSiteModule.platform.isCommon()
        return isCommon || (element as? KtDeclaration)?.isExpectDeclaration() != true
    }

    context(KtAnalysisSession)
    fun getKotlinClassesByName(
        name: Name,
        psiFilter: (KtClassLikeDeclaration) -> Boolean = { true },
    ): Sequence<KtClassLikeSymbol> {
        val isCommon = useSiteModule.platform.isCommon()

        val valueFilter: (KtClassLikeDeclaration) -> Boolean = { psiFilter(it) && useSiteFilter(it) }
        val resolveExtensionScope = getResolveExtensionScopeWithTopLevelDeclarations()

        return getClassLikeSymbols(
            classDeclarations = KotlinClassShortNameIndex.getAllElements(name.asString(), project, scope, valueFilter),
            typeAliasDeclarations = KotlinTypeAliasShortNameIndex.getAllElements(name.asString(), project, scope, valueFilter),
            declarationsFromExtension = resolveExtensionScope.getClassifierSymbols(name).filterIsInstance<KtClassLikeSymbol>(),
        )
    }

    context(KtAnalysisSession)
    fun getKotlinClassesByNameFilter(
        nameFilter: (Name) -> Boolean,
        psiFilter: (KtClassLikeDeclaration) -> Boolean = { true },
    ): Sequence<KtClassLikeSymbol> {
        val isCommon = useSiteModule.platform.isCommon()

        val keyFilter: (String) -> Boolean = { nameFilter(getShortName(it)) }
        val valueFilter: (KtClassLikeDeclaration) -> Boolean = { psiFilter(it) && useSiteFilter(it) }
        val resolveExtensionScope = getResolveExtensionScopeWithTopLevelDeclarations()

        return getClassLikeSymbols(
            classDeclarations = KotlinFullClassNameIndex.getAllElements(project, scope, keyFilter, valueFilter),
            typeAliasDeclarations = KotlinTypeAliasShortNameIndex.getAllElements(project, scope, keyFilter, valueFilter),
            declarationsFromExtension = resolveExtensionScope.getClassifierSymbols(nameFilter).filterIsInstance<KtClassLikeSymbol>(),
        )
    }

    context(KtAnalysisSession)
    private fun getClassLikeSymbols(
        classDeclarations: List<KtClassOrObject>,
        typeAliasDeclarations: List<KtTypeAlias>,
        declarationsFromExtension: Sequence<KtClassLikeSymbol>
    ): Sequence<KtClassLikeSymbol> = sequence {
        for (ktClassOrObject in classDeclarations) {
            yieldIfNotNull(ktClassOrObject.getNamedClassOrObjectSymbol())
        }
        for (typeAlias in typeAliasDeclarations) {
            yield(typeAlias.getTypeAliasSymbol())
        }
        yieldAll(declarationsFromExtension)
    }

    context(KtAnalysisSession)
    fun getJavaClassesByNameFilter(
        nameFilter: (Name) -> Boolean,
        psiFilter: (PsiClass) -> Boolean = { true }
    ): Sequence<KtNamedClassOrObjectSymbol> {
        val names = buildSet {
            forEachNonKotlinCache { cache ->
                cache.processAllClassNames({ nameString ->
                    if (!Name.isValidIdentifier(nameString)) return@processAllClassNames true
                    val name = Name.identifier(nameString)
                    if (nameFilter(name)) { add(name) }
                    true
                }, scope, null)
            }
        }

        return sequence {
            names.forEach { name ->
                yieldAll(getJavaClassesByName(name, psiFilter))
            }
        }
    }


    context(KtAnalysisSession)
    fun getJavaClassesByName(
        name: Name,
        psiFilter: (PsiClass) -> Boolean = { true }
    ): Sequence<KtNamedClassOrObjectSymbol> {
        val nameString = name.asString()

        return sequence {
            forEachNonKotlinCache { cache ->
                yieldAll(cache.getClassesByName(nameString, scope).iterator())
            }
        }
            .filter { psiFilter(it) && useSiteFilter(it) }
            .mapNotNull { it.getNamedClassSymbol() }
    }

    context(KtAnalysisSession)
    fun getKotlinCallableSymbolsByName(
        name: Name,
        psiFilter: (KtCallableDeclaration) -> Boolean = { true },
    ): Sequence<KtCallableSymbol> {
        val nameString = name.asString()

        val values = SmartList<KtNamedDeclaration>()
        val processor = CancelableCollectFilterProcessor(values) {
            it is KtCallableDeclaration && psiFilter(it) && useSiteFilter(it) && !it.isKotlinBuiltins()
        }
        KotlinFunctionShortNameIndex.processElements(nameString, project, scope, processor)
        KotlinPropertyShortNameIndex.processElements(nameString, project, scope, processor)

        return sequence {
            for (callableDeclaration in values) {
                yieldIfNotNull(callableDeclaration.getSymbolOfTypeSafe<KtCallableSymbol>())
            }
            yieldAll(
                getResolveExtensionScopeWithTopLevelDeclarations().getCallableSymbols(name)
            )
        }
    }

    context(KtAnalysisSession)
    fun getJavaCallableSymbolsByName(
        name: Name,
        psiFilter: (PsiMember) -> Boolean = { true }
    ): Sequence<KtCallableSymbol> {
        val nameString = name.asString()

        return sequence {
            forEachNonKotlinCache { cache -> yieldAll(cache.getMethodsByName(nameString, scope).iterator()) }
            forEachNonKotlinCache { cache -> yieldAll(cache.getFieldsByName(nameString, scope).iterator()) }
        }
            .filter { psiFilter(it) && useSiteFilter(it) }
            .mapNotNull { it.getCallableSymbol() }

    }


    /**
     *  Returns top-level callables, excluding extensions. To obtain extensions use [getTopLevelExtensionCallableSymbolsByNameFilter].
     */
    context(KtAnalysisSession)
    fun getTopLevelCallableSymbolsByNameFilter(
        nameFilter: (Name) -> Boolean,
        psiFilter: (KtCallableDeclaration) -> Boolean = { true }
    ): Sequence<KtCallableSymbol> {
        val values = SmartList<KtCallableDeclaration>()
        val processor = CancelableCollectFilterProcessor(values) {
            psiFilter(it) && useSiteFilter(it) && !it.isKotlinBuiltins() && it.receiverTypeReference == null
        }

        val keyFilter: (String) -> Boolean = { nameFilter(getShortName(it)) }
        KotlinTopLevelFunctionFqnNameIndex.processAllElements(project, scope, keyFilter, processor)
        KotlinTopLevelPropertyFqnNameIndex.processAllElements(project, scope, keyFilter, processor)

        return sequence {
            for (callableDeclaration in values) {
                yieldIfNotNull(callableDeclaration.getSymbolOfTypeSafe<KtCallableSymbol>())
            }
            yieldAll(
                getResolveExtensionScopeWithTopLevelDeclarations().getCallableSymbols(nameFilter).filter { !it.isExtension }
            )
        }
    }

    context(KtAnalysisSession)
    fun getTopLevelExtensionCallableSymbolsByName(
        name: Name,
        receiverTypes: List<KtType>,
        psiFilter: (KtCallableDeclaration) -> Boolean = { true }
    ): Sequence<KtCallableSymbol> {
        val receiverTypeNames = receiverTypes.flatMapTo(hashSetOf()) { findAllNamesForType(it) }
        if (receiverTypeNames.isEmpty()) return emptySequence()

        val keys = receiverTypeNames.map { KotlinTopLevelExtensionsByReceiverTypeIndex.buildKey(receiverTypeName = it, name.asString()) }
        val valueFilter: (KtCallableDeclaration) -> Boolean = { psiFilter(it) && useSiteFilter(it) && !it.isKotlinBuiltins() }
        val values = keys.flatMap { key -> KotlinTopLevelExtensionsByReceiverTypeIndex.getAllElements(key, project, scope, valueFilter) }

        return sequence {
            for (extension in values) {
                yieldIfNotNull(extension.getSymbolOfTypeSafe<KtCallableSymbol>())
            }
            val resolveExtensionScope = getResolveExtensionScopeWithTopLevelDeclarations()
            yieldAll(resolveExtensionScope.getCallableSymbols(name).filterExtensionsByReceiverTypes(receiverTypes))
        }
    }

    context(KtAnalysisSession)
    fun getTopLevelExtensionCallableSymbolsByNameFilter(
        nameFilter: (Name) -> Boolean,
        receiverTypes: List<KtType>,
        psiFilter: (KtCallableDeclaration) -> Boolean = { true }
    ): Sequence<KtCallableSymbol> {
        val receiverTypeNames = receiverTypes.flatMapTo(hashSetOf()) { findAllNamesForType(it) }
        if (receiverTypeNames.isEmpty()) return emptySequence()

        val keyFilter: (String) -> Boolean = { key ->
            val receiverTypeName = KotlinTopLevelExtensionsByReceiverTypeIndex.receiverTypeNameFromKey(key)
            val callableName = KotlinTopLevelExtensionsByReceiverTypeIndex.callableNameFromKey(key)
            receiverTypeName in receiverTypeNames && nameFilter(Name.identifier(callableName))
        }
        val valueFilter: (KtCallableDeclaration) -> Boolean = { psiFilter(it) && useSiteFilter(it) && !it.isKotlinBuiltins() }
        val values = KotlinTopLevelExtensionsByReceiverTypeIndex.getAllElements(project, scope, keyFilter, valueFilter)

        return sequence {
            for (extension in values) {
                yieldIfNotNull(extension.getSymbolOfTypeSafe<KtCallableSymbol>())
            }
            val resolveExtensionScope = getResolveExtensionScopeWithTopLevelDeclarations()
            yieldAll(resolveExtensionScope.getCallableSymbols(nameFilter).filterExtensionsByReceiverTypes(receiverTypes))
        }
    }

    context(KtAnalysisSession)
    private fun Sequence<KtCallableSymbol>.filterExtensionsByReceiverTypes(receiverTypes: List<KtType>): Sequence<KtCallableSymbol> {
        val nonNullableReceiverTypes = receiverTypes.map { it.withNullability(KtTypeNullability.NON_NULLABLE) }

        return filter { symbol ->
            if (!symbol.isExtension) return@filter false
            val symbolReceiverType = symbol.receiverType ?: return@filter false

            nonNullableReceiverTypes.any { it isPossiblySubTypeOf symbolReceiverType }
        }
    }

    private inline fun forEachNonKotlinCache(action: (cache: PsiShortNamesCache) -> Unit) {
        for (cache in PsiShortNamesCache.EP_NAME.getExtensions(project)) {
            if (cache::class.java.name == "org.jetbrains.kotlin.idea.caches.KotlinShortNamesCache") continue
            action(cache)
        }
    }

    private fun getShortName(fqName: String) = Name.identifier(fqName.substringAfterLast('.'))

    context(KtAnalysisSession)
    private fun findAllNamesForType(type: KtType): Set<String> = buildSet {
        if (type is KtFlexibleType) {
            return findAllNamesForType(type.lowerBound)
        }
        if (type !is KtNonErrorClassType) return@buildSet

        val typeName = type.classId.shortClassName.let {
            if (it.isSpecial) return@buildSet
            it.identifier
        }

        add(typeName)
        addAll(getPossibleTypeAliasExpansionNames(typeName))

        val superTypes = (type.classSymbol as? KtClassOrObjectSymbol)?.superTypes
        superTypes?.forEach { superType ->
            addAll(findAllNamesForType(superType))
        }
    }

    private fun getPossibleTypeAliasExpansionNames(originalTypeName: String): Set<String> = buildSet {
        fun searchRecursively(typeName: String) {
            ProgressManager.checkCanceled()
            KotlinTypeAliasByExpansionShortNameIndex[typeName, project, scope]
                .asSequence()
                .mapNotNull { it.name }
                .filter { add(it) }
                .forEach(::searchRecursively)
        }

        searchRecursively(originalTypeName)
    }

    companion object {
        fun createForElement(useSiteKtElement: KtElement): KtSymbolFromIndexProvider = analyze(useSiteKtElement) {
            KtSymbolFromIndexProvider(useSiteKtElement.containingKtFile, analysisScope)
        }
    }
}

private val KotlinBuiltins = setOf("kotlin/ArrayIntrinsicsKt", "kotlin/internal/ProgressionUtilKt")
fun KtCallableDeclaration.isKotlinBuiltins(): Boolean {
    val file = containingKtFile
    val virtualFile = file.virtualFile
    if (virtualFile.extension == "kotlin_metadata") return true
    if (this !is KtNamedFunction) return false
    return file.packageFqName.asString().replace(".", "/") + "/" + virtualFile.nameWithoutExtension in KotlinBuiltins
}