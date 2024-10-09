// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.util.SmartList
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaIntersectionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.base.analysis.isExcludedFromAutoImport
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.serialization.deserialization.METADATA_FILE_EXTENSION
import org.jetbrains.kotlin.utils.yieldIfNotNull

@OptIn(KaExperimentalApi::class)
class KtSymbolFromIndexProvider private constructor(
    private val useSiteFile: KtFile,
    private val scope: GlobalSearchScope,
) {
    private val project: Project
        get() = useSiteFile.project

    context(KaSession)
    private fun useSiteFilter(element: PsiElement): Boolean {
        if (element.kotlinFqName?.isExcludedFromAutoImport(project, useSiteFile) == true) return false

        val isCommon = useSiteModule.targetPlatform.isCommon()
        return isCommon || (element as? KtDeclaration)?.isExpectDeclaration() != true
    }

    context(KaSession)
    fun getKotlinClassesByName(
        name: Name,
        psiFilter: (KtClassLikeDeclaration) -> Boolean = { true },
    ): Sequence<KaClassLikeSymbol> {
        val valueFilter: (KtClassLikeDeclaration) -> Boolean = { psiFilter(it) && useSiteFilter(it) }
        val resolveExtensionScope = resolveExtensionScopeWithTopLevelDeclarations

        return getClassLikeSymbols(
            classDeclarations = KotlinClassShortNameIndex.getAllElements(name.asString(), project, scope, valueFilter),
            typeAliasDeclarations = KotlinTypeAliasShortNameIndex.getAllElements(name.asString(), project, scope, valueFilter),
            declarationsFromExtension = resolveExtensionScope.classifiers(name).filterIsInstance<KaClassLikeSymbol>(),
        )
    }

    context(KaSession)
    fun getKotlinClassesByNameFilter(
        nameFilter: (Name) -> Boolean,
        psiFilter: (KtClassLikeDeclaration) -> Boolean = { true },
    ): Sequence<KaClassLikeSymbol> {
        val keyFilter: (String) -> Boolean = { nameFilter(getShortName(it)) }
        val valueFilter: (KtClassLikeDeclaration) -> Boolean = { psiFilter(it) && useSiteFilter(it) }
        val resolveExtensionScope = resolveExtensionScopeWithTopLevelDeclarations

        return getClassLikeSymbols(
            classDeclarations = KotlinFullClassNameIndex.getAllElements(project, scope, keyFilter, valueFilter),
            typeAliasDeclarations = KotlinTypeAliasShortNameIndex.getAllElements(project, scope, keyFilter, valueFilter),
            declarationsFromExtension = resolveExtensionScope.classifiers(nameFilter).filterIsInstance<KaClassLikeSymbol>(),
        )
    }

    context(KaSession)
    private fun getClassLikeSymbols(
        classDeclarations: List<KtClassOrObject>,
        typeAliasDeclarations: List<KtTypeAlias>,
        declarationsFromExtension: Sequence<KaClassLikeSymbol>
    ): Sequence<KaClassLikeSymbol> = sequence {
        for (ktClassOrObject in classDeclarations) {
            yieldIfNotNull(ktClassOrObject.namedClassSymbol)
        }
        for (typeAlias in typeAliasDeclarations) {
            yield(typeAlias.symbol)
        }
        yieldAll(declarationsFromExtension)
    }

    context(KaSession)
    fun getJavaClassesByNameFilter(
        nameFilter: (Name) -> Boolean,
        psiFilter: (PsiClass) -> Boolean = { true }
    ): Sequence<KaNamedClassSymbol> {
        val names = buildSet {
            nonKotlinNamesCaches.forEach { cache ->
                cache.processAllClassNames({ nameString ->
                                               if (!Name.isValidIdentifier(nameString)) return@processAllClassNames true
                                               val name = Name.identifier(nameString)
                                               if (nameFilter(name)) {
                                                   add(name)
                                               }
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


    context(KaSession)
    fun getJavaClassesByName(
        name: Name,
        psiFilter: (PsiClass) -> Boolean = { true }
    ): Sequence<KaNamedClassSymbol> {
        val nameString = name.asString()

        return nonKotlinNamesCaches.flatMap { cache ->
            cache.getClassesByName(nameString, scope).asSequence()
        }.filter { psiFilter(it) && useSiteFilter(it) }
            .mapNotNull { it.namedClassSymbol }
    }

    context(KaSession)
    fun getKotlinCallableSymbolsByName(
        name: Name,
        psiFilter: (KtCallableDeclaration) -> Boolean = { true },
    ): Sequence<KaCallableSymbol> {
        val nameString = name.asString()

        val values = SmartList<KtNamedDeclaration>()
        val processor = CancelableCollectFilterProcessor(values) {
            it is KtCallableDeclaration && psiFilter(it) && useSiteFilter(it) && !it.isKotlinBuiltins()
        }
        KotlinFunctionShortNameIndex.processElements(nameString, project, scope, processor)
        KotlinPropertyShortNameIndex.processElements(nameString, project, scope, processor)

        return sequence {
            for (callableDeclaration in values) {
                yieldIfNotNull(callableDeclaration.symbol as? KaCallableSymbol)
            }
            yieldAll(
                resolveExtensionScopeWithTopLevelDeclarations.callables(name)
            )
        }
    }

    context(KaSession)
    fun getJavaCallableSymbolsByName(
        name: Name,
        psiFilter: (PsiMember) -> Boolean = { true }
    ): Sequence<KaCallableSymbol> {
        val nameString = name.asString()

        return nonKotlinNamesCaches.flatMap { cache ->
            cache.getMethodsByName(nameString, scope).asSequence() +
                    cache.getFieldsByName(nameString, scope)
        }.filter { psiFilter(it) && useSiteFilter(it) }
            .mapNotNull { it.callableSymbol }
    }

    /**
     *  Returns top-level callables, excluding extensions. To obtain extensions use [getExtensionCallableSymbolsByNameFilter].
     */
    context(KaSession)
    fun getTopLevelCallableSymbolsByNameFilter(
        nameFilter: (Name) -> Boolean,
        psiFilter: (KtCallableDeclaration) -> Boolean = { true }
    ): Sequence<KaCallableSymbol> {
        val values = SmartList<KtCallableDeclaration>()
        val processor = CancelableCollectFilterProcessor(values) {
            psiFilter(it) && useSiteFilter(it) && !it.isKotlinBuiltins() && it.receiverTypeReference == null
        }

        val keyFilter: (String) -> Boolean = { nameFilter(getShortName(it)) }
        KotlinTopLevelFunctionFqnNameIndex.processAllElements(project, scope, keyFilter, processor)
        KotlinTopLevelPropertyFqnNameIndex.processAllElements(project, scope, keyFilter, processor)

        return sequence {
            for (callableDeclaration in values) {
                yieldIfNotNull(callableDeclaration.symbol as? KaCallableSymbol)
            }
            yieldAll(
                resolveExtensionScopeWithTopLevelDeclarations.callables(nameFilter).filter { !it.isExtension }
            )
        }
    }

    context(KaSession)
    fun getExtensionCallableSymbolsByName(
        name: Name,
        receiverTypes: List<KaType>,
        psiFilter: (KtCallableDeclaration) -> Boolean,
    ): Sequence<KaCallableSymbol> {
        val receiverTypeNames = findAllNamesForTypes(receiverTypes)
        if (receiverTypeNames.isEmpty()) return emptySequence()

        val values = receiverTypeNames.asSequence()
            .flatMap { receiverTypeName ->
                sequenceOf(
                    KotlinTopLevelExtensionsByReceiverTypeIndex,
                    KotlinExtensionsInObjectsByReceiverTypeIndex,
                ).flatMap { indexHelper ->
                    val key = KotlinExtensionsByReceiverTypeStubIndexHelper.Companion.Key(receiverTypeName, name)

                    indexHelper.getAllElements(key.key, project, scope) {
                        psiFilter(it)
                                && useSiteFilter(it)
                                && !it.isKotlinBuiltins()
                    }
                }
            }.map { it.symbol }
            .filterIsInstance<KaCallableSymbol>()

        return sequence {
            yieldAll(values)
            yieldAll(resolveExtensionScopeWithTopLevelDeclarations.callables(name).filterExtensionsByReceiverTypes(receiverTypes))
        }
    }

    context(KaSession)
    fun getExtensionCallableSymbolsByNameFilter(
        nameFilter: (Name) -> Boolean,
        receiverTypes: List<KaType>,
        psiFilter: (KtCallableDeclaration) -> Boolean = { true },
    ): Sequence<KaCallableSymbol> {
        val receiverTypeNames = findAllNamesForTypes(receiverTypes)
        if (receiverTypeNames.isEmpty()) return emptySequence()

        val keyFilter: (String) -> Boolean = { key ->
            val (receiverTypeName, callableName) = KotlinExtensionsByReceiverTypeStubIndexHelper.Companion.Key(key)
            receiverTypeName in receiverTypeNames
                    && nameFilter(callableName)
        }

        val values = sequenceOf(
            KotlinTopLevelExtensionsByReceiverTypeIndex,
            KotlinExtensionsInObjectsByReceiverTypeIndex,
        ).flatMap { index ->
            index.getAllElements(project, scope, keyFilter) {
                psiFilter(it)
                        && useSiteFilter(it)
                        && !it.isKotlinBuiltins()
            }
        }.map { it.symbol }
            .filterIsInstance<KaCallableSymbol>()

        return sequence {
            yieldAll(values)
            yieldAll(resolveExtensionScopeWithTopLevelDeclarations.callables(nameFilter).filterExtensionsByReceiverTypes(receiverTypes))
        }
    }

    context(KaSession)
    private fun Sequence<KaCallableSymbol>.filterExtensionsByReceiverTypes(receiverTypes: List<KaType>): Sequence<KaCallableSymbol> {
        val nonNullableReceiverTypes = receiverTypes.map { it.withNullability(KaTypeNullability.NON_NULLABLE) }

        return filter { symbol ->
            if (!symbol.isExtension) return@filter false
            val symbolReceiverType = symbol.receiverType ?: return@filter false

            nonNullableReceiverTypes.any { it isPossiblySubTypeOf symbolReceiverType }
        }
    }

    private inline val nonKotlinNamesCaches: Sequence<PsiShortNamesCache>
        get() = PsiShortNamesCache.EP_NAME // PsiShortNamesCache should have been a project-level EP
            .getPoint(project) // could have been ::lazySequence in this case
            .let { it as ExtensionPointImpl<PsiShortNamesCache> }
            .filterNot { it::class.java.name == "org.jetbrains.kotlin.idea.caches.KotlinShortNamesCache" }

    private fun getShortName(fqName: String) = Name.identifier(fqName.substringAfterLast('.'))

    context(KaSession)
    private fun findAllNamesForTypes(types: List<KaType>): Set<Name> =
        types.flatMapTo(hashSetOf()) { findAllNamesForType(it) }

    context(KaSession)
    private fun findAllNamesForType(type: KaType): Set<Name> {
        if (type is KaFlexibleType) {
            return findAllNamesForType(type.lowerBound)
        }

        if (type is KaIntersectionType) {
            return findAllNamesForTypes(type.conjuncts)
        }

        if (type is KaTypeParameterType) {
            // when no explicit upper bounds, we consider `Any` to be an upper bound
            val upperBounds = type.symbol.upperBounds.ifEmpty { listOf(builtinTypes.any) }

            return findAllNamesForTypes(upperBounds)
        }

        if (type !is KaClassType) return emptySet()

        val typeName = type.classId
            .shortClassName
            .takeUnless { it.isSpecial }
            ?: return emptySet()

        return buildSet {
            add(typeName)
            addAll(getPossibleTypeAliasExpansionNames(typeName))

            val superTypes = (type.symbol as? KaClassSymbol)?.superTypes
            superTypes?.forEach { superType ->
                addAll(findAllNamesForType(superType))
            }
        }
    }

    private fun getPossibleTypeAliasExpansionNames(originalTypeName: Name): Set<Name> = buildSet {
        fun searchRecursively(typeName: Name) {
            ProgressManager.checkCanceled()
            KotlinTypeAliasByExpansionShortNameIndex[typeName.identifier, project, scope]
                .asSequence()
                .mapNotNull { it.nameAsName }
                .filter(::add)
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
    if (virtualFile.extension == METADATA_FILE_EXTENSION) return true
    if (this !is KtNamedFunction) return false
    return file.packageFqName.asString().replace(".", "/") + "/" + virtualFile.nameWithoutExtension in KotlinBuiltins
}