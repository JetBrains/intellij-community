// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.util.Processor
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaBuiltinTypes
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.*
import org.jetbrains.kotlin.base.analysis.isExcludedFromAutoImport
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.kotlin.serialization.deserialization.METADATA_FILE_EXTENSION

class KtSymbolFromIndexProvider(
    private val file: KtFile,
) {

    private val project: Project
        get() = file.project

    context(KaSession)
    private fun <T : PsiElement> T.isAcceptable(psiFilter: (T) -> Boolean): Boolean {
        if (!psiFilter(this)) return false

        if (kotlinFqName?.isExcludedFromAutoImport(project, file) == true) return false

        return this !is KtDeclaration
                || !isExpectDeclaration()
                || useSiteModule.targetPlatform.isCommon()
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun getKotlinClassesByName(
        name: Name,
        scope: GlobalSearchScope = analysisScope,
        psiFilter: (KtClassLikeDeclaration) -> Boolean = { true },
    ): Sequence<KaClassLikeSymbol> {
        val resolveExtensionScope = resolveExtensionScopeWithTopLevelDeclarations

        return getClassLikeSymbols(
            classDeclarations = KotlinClassShortNameIndex.getAllElements(name.asString(), project, scope) {
                it.isAcceptable(psiFilter)
            },
            typeAliasDeclarations = KotlinTypeAliasShortNameIndex.getAllElements(name.asString(), project, scope) {
                it.isAcceptable(psiFilter)
            },
            declarationsFromExtension = resolveExtensionScope.classifiers(name).filterIsInstance<KaClassLikeSymbol>(),
        )
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun getKotlinClassesByNameFilter(
        nameFilter: (Name) -> Boolean,
        scope: GlobalSearchScope = analysisScope,
        psiFilter: (KtClassLikeDeclaration) -> Boolean = { true },
    ): Sequence<KaClassLikeSymbol> {
        val keyFilter: (String) -> Boolean = { nameFilter(getShortName(it)) }
        val resolveExtensionScope = resolveExtensionScopeWithTopLevelDeclarations

        return getClassLikeSymbols(
            classDeclarations = KotlinFullClassNameIndex.getAllElements(project, scope, keyFilter) {
                it.isAcceptable(psiFilter)
            },
            typeAliasDeclarations = KotlinTypeAliasShortNameIndex.getAllElements(project, scope, keyFilter) {
                it.isAcceptable(psiFilter)
            },
            declarationsFromExtension = resolveExtensionScope.classifiers(nameFilter).filterIsInstance<KaClassLikeSymbol>(),
        )
    }

    context(KaSession)
    private fun getClassLikeSymbols(
        classDeclarations: Sequence<KtClassOrObject>,
        typeAliasDeclarations: Sequence<KtTypeAlias>,
        declarationsFromExtension: Sequence<KaClassLikeSymbol>
    ): Sequence<KaClassLikeSymbol> =
        classDeclarations.mapNotNull { it.namedClassSymbol } +
                typeAliasDeclarations.map { it.symbol } +
                declarationsFromExtension

    context(KaSession)
    @KaExperimentalApi
    fun getKotlinEnumEntriesByNameFilter(
        nameFilter: (Name) -> Boolean,
        scope: GlobalSearchScope = analysisScope,
        psiFilter: (KtEnumEntry) -> Boolean = { true },
    ): Sequence<KaEnumEntrySymbol> = KotlinFullClassNameIndex.getAllElements<KtEnumEntry>(
        project = project,
        scope = scope,
        keyFilter = { nameFilter(getShortName(it)) },
    ) { it.isAcceptable(psiFilter) }
        .map { it.symbol }

    context(KaSession)
    fun getJavaClassesByNameFilter(
        nameFilter: (Name) -> Boolean,
        scope: GlobalSearchScope = analysisScope,
        psiFilter: (PsiClass) -> Boolean = { true }
    ): Sequence<KaNamedClassSymbol> {
        val names = buildSet {
            val processor = createNamesProcessor(nameFilter)

            nonKotlinNamesCaches.forEach {
                it.processAllClassNames(processor, scope, null)
            }
        }

        return names.asSequence()
            .flatMap { getJavaClassesByName(it, scope, psiFilter) }
    }

    context(KaSession)
    fun getJavaClassesByName(
        name: Name,
        scope: GlobalSearchScope = analysisScope,
        psiFilter: (PsiClass) -> Boolean = { true }
    ): Sequence<KaNamedClassSymbol> {
        val nameString = name.asString()

        return nonKotlinNamesCaches.flatMap { cache ->
            cache.getClassesByName(nameString, scope).asSequence()
        }.filter { it.isAcceptable(psiFilter) }
            .mapNotNull { it.namedClassSymbol }
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun getKotlinCallableSymbolsByNameFilter(
        nameFilter: (Name) -> Boolean,
        scope: GlobalSearchScope = analysisScope,
        psiFilter: (KtCallableDeclaration) -> Boolean = { true },
    ): Sequence<KaCallableSymbol> = sequenceOf(
        KotlinFunctionShortNameIndex,
        KotlinPropertyShortNameIndex,
    ).flatMap { index ->
        index.getAllElements<KtCallableDeclaration>(
            project = project,
            scope = scope,
            keyFilter = { nameFilter(getShortName(it)) },
        ) { declaration ->
            declaration.isAcceptable(psiFilter)
                    && !declaration.isExtensionDeclaration()
                    && !declaration.isKotlinBuiltins()
        }
    }.map { it.symbol }
        .filterIsInstance<KaCallableSymbol>() +
            resolveExtensionScopeWithTopLevelDeclarations.callables(nameFilter)

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun getKotlinCallableSymbolsByName(
        name: Name,
        scope: GlobalSearchScope = analysisScope,
        psiFilter: (KtCallableDeclaration) -> Boolean = { true },
    ): Sequence<KaCallableSymbol> = sequenceOf(
        KotlinFunctionShortNameIndex,
        KotlinPropertyShortNameIndex,
    ).flatMap { helper ->
        val processor = CancelableCollectFilterProcessor { declaration: KtNamedDeclaration ->
            declaration is KtCallableDeclaration
                    && declaration.isAcceptable(psiFilter)
                    && !declaration.isKotlinBuiltins()
        }

        helper.processElements(
            key = name.asString(),
            project = project,
            scope = scope,
            processor = processor,
        )

        processor.results
    }.map { it.symbol }
        .filterIsInstance<KaCallableSymbol>() +
            resolveExtensionScopeWithTopLevelDeclarations.callables(name)

    context(KaSession)
    fun getJavaFieldsByNameFilter(
        nameFilter: (Name) -> Boolean,
        scope: GlobalSearchScope = analysisScope,
        psiFilter: (PsiField) -> Boolean = { true }
    ): Sequence<KaCallableSymbol> {
        val names = buildSet {
            val processor = createNamesProcessor(nameFilter)

            nonKotlinNamesCaches.forEach {
                it.processAllFieldNames(processor, scope, null)
            }
        }

        return names.asSequence()
            .flatMap { getJavaFieldsByName(it, scope, psiFilter) }
    }

    context(KaSession)
    fun getJavaMethodsByName(
        name: Name,
        scope: GlobalSearchScope = analysisScope,
        psiFilter: (PsiMethod) -> Boolean = { true },
    ): Sequence<KaCallableSymbol> = nonKotlinNamesCaches.flatMap {
        it.getMethodsByName(name.asString(), scope).asSequence()
    }.filter { it.isAcceptable(psiFilter) }
        .mapNotNull { it.callableSymbol }

    context(KaSession)
    fun getJavaFieldsByName(
        name: Name,
        scope: GlobalSearchScope = analysisScope,
        psiFilter: (PsiField) -> Boolean = { true },
    ): Sequence<KaCallableSymbol> = nonKotlinNamesCaches.flatMap {
        it.getFieldsByName(name.asString(), scope).asSequence()
    }.filter { it.isAcceptable(psiFilter) }
        .mapNotNull { it.callableSymbol }

    /**
     *  Returns top-level callables, excluding extensions. To obtain extensions use [getExtensionCallableSymbolsByNameFilter].
     */
    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun getTopLevelCallableSymbolsByNameFilter(
        nameFilter: (Name) -> Boolean,
        scope: GlobalSearchScope = analysisScope,
        psiFilter: (KtCallableDeclaration) -> Boolean = { true }
    ): Sequence<KaCallableSymbol> = sequenceOf(
        KotlinTopLevelFunctionFqnNameIndex,
        KotlinTopLevelPropertyFqnNameIndex,
    ).flatMap { helper ->
        val processor = CancelableCollectFilterProcessor { declaration: KtCallableDeclaration ->
            declaration.isAcceptable(psiFilter)
                    && !declaration.isKotlinBuiltins()
                    && declaration.receiverTypeReference == null
        }

        helper.processAllElements(
            project = project,
            scope = scope,
            filter = { nameFilter(getShortName(it)) },
            processor = processor,
        )

        processor.results
    }.map { it.symbol }
        .filterIsInstance<KaCallableSymbol>() +
            resolveExtensionScopeWithTopLevelDeclarations.callables(nameFilter)
                .filterNot { it.isExtension }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    fun getExtensionCallableSymbolsByName(
        name: Name,
        receiverTypes: List<KaType>,
        scope: GlobalSearchScope = analysisScope,
        psiFilter: (KtCallableDeclaration) -> Boolean = { true },
    ): Sequence<KaCallableSymbol> {
        val receiverTypeNames = findAllNamesForTypes(
            project = project,
            types = receiverTypes,
            scope = scope,
            builtinTypes = builtinTypes,
        )
        if (receiverTypeNames.isEmpty()) return emptySequence()

        val values = receiverTypeNames.asSequence()
            .flatMap { receiverTypeName ->
                sequenceOf(
                    KotlinTopLevelExtensionsByReceiverTypeIndex,
                    KotlinExtensionsInObjectsByReceiverTypeIndex,
                ).flatMap { indexHelper ->
                    val key = KotlinExtensionsByReceiverTypeStubIndexHelper.Companion.Key(receiverTypeName, name)

                    indexHelper.getAllElements(key.key, project, scope) { declaration ->
                                declaration.isAcceptable(psiFilter)
                                && !declaration.isKotlinBuiltins()
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
    @OptIn(KaExperimentalApi::class)
    fun getExtensionCallableSymbolsByNameFilter(
        nameFilter: (Name) -> Boolean,
        receiverTypes: List<KaType>,
        scope: GlobalSearchScope = analysisScope,
        psiFilter: (KtCallableDeclaration) -> Boolean = { true },
    ): Sequence<KaCallableSymbol> {
        val receiverTypeNames = findAllNamesForTypes(
            project = project,
            types = receiverTypes,
            scope = scope,
            builtinTypes = builtinTypes,
        )
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
            index.getAllElements(project, scope, keyFilter) { declaration: KtCallableDeclaration ->
                declaration.isAcceptable(psiFilter)
                        && !declaration.isKotlinBuiltins()
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

}

private fun findAllNamesForTypes(
    project: Project,
    types: List<KaType>,
    scope: GlobalSearchScope,
    builtinTypes: KaBuiltinTypes,
): Set<Name> {
    fun findAllNamesForType(type: KaType): Set<Name> = when (type) {
        is KaFlexibleType -> findAllNamesForType(type.lowerBound)

        is KaIntersectionType -> findAllNamesForTypes(project, type.conjuncts, scope, builtinTypes)

        is KaTypeParameterType -> {
            // when no explicit upper bounds, we consider `Any` to be an upper bound
            val upperBounds = type.symbol
                .upperBounds
                .ifEmpty { listOf(builtinTypes.any) }

            findAllNamesForTypes(project, upperBounds, scope, builtinTypes)
        }

        is KaClassType -> {
            val typeName = type.classId
                .shortClassName

            if (typeName.isSpecial)
                emptySet()
            else
                buildSet {
                    add(typeName)
                    addAll(getPossibleTypeAliasExpansionNames(project, typeName, scope))

                    (type.symbol as? KaClassSymbol)
                        ?.superTypes
                        ?.asSequence()
                        ?.flatMap { findAllNamesForType(it) }
                        ?.forEach { add(it) }
                }
        }

        else -> emptySet()
    }

    return types.flatMapTo(mutableSetOf()) {
        findAllNamesForType(it)
    }
}

private fun getPossibleTypeAliasExpansionNames(
    project: Project,
    originalTypeName: Name,
    scope: GlobalSearchScope,
): Set<Name> = buildSet {
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

private val KotlinBuiltins = setOf(
    "kotlin/ArrayIntrinsicsKt",
    "kotlin/internal/ProgressionUtilKt",
)

private fun KtCallableDeclaration.isKotlinBuiltins(): Boolean {
    val file = containingKtFile
    val virtualFile = file.virtualFile
    if (virtualFile.extension == METADATA_FILE_EXTENSION) return true
    if (this !is KtNamedFunction) return false
    return file.packageFqName.asString().replace(".", "/") + "/" + virtualFile.nameWithoutExtension in KotlinBuiltins
}

private fun MutableSet<Name>.createNamesProcessor(
    nameFilter: (Name) -> Boolean,
) = Processor { name: String ->
    Name.identifierIfValid(name)
        ?.takeIf(nameFilter)
        ?.let(this@createNamesProcessor::add)
    true
}
