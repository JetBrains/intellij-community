// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMember
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.util.SmartList
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.getSymbolOfTypeSafe
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.idea.base.psi.isExpectDeclaration
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class KtSymbolFromIndexProvider(private val project: Project) {
    context(KtAnalysisSession)
    fun getKotlinClassesByName(
        name: Name,
        psiFilter: (KtClassOrObject) -> Boolean = { true },
    ): Sequence<KtNamedClassOrObjectSymbol> {
        val scope = analysisScope
        val isCommon = useSiteModule.platform.isCommon()
        val values = KotlinClassShortNameIndex.getAllElements(
            name.asString(),
            project,
            scope
        ) {
            (isCommon || !it.isExpectDeclaration()) && psiFilter(it)
        }
        return sequence {
            for (value in values) {
                value.getNamedClassOrObjectSymbol()?.let { yield(it) }
            }
            yieldAll(
                getResolveExtensionScopeWithTopLevelDeclarations().getClassifierSymbols { it == name }.filterIsInstance<KtNamedClassOrObjectSymbol>()
            )
        }
    }

    context(KtAnalysisSession)
    fun getKotlinClassesByNameFilter(
        nameFilter: (Name) -> Boolean,
        psiFilter: (KtClassOrObject) -> Boolean = { true },
    ): Sequence<KtNamedClassOrObjectSymbol> {
        val scope = analysisScope
        val isCommon = useSiteModule.platform.isCommon()
        val values = KotlinFullClassNameIndex.getAllElements(
            project,
            scope,
            keyFilter = { nameFilter(getShortName(it)) },
            valueFilter = { (isCommon || !it.isExpectDeclaration()) && psiFilter(it) }
        )
        return sequence {
            for (ktClassOrObject in values) {
                ktClassOrObject.getNamedClassOrObjectSymbol()?.let { yield(it) }
            }
            yieldAll(
                getResolveExtensionScopeWithTopLevelDeclarations().getClassifierSymbols(nameFilter).filterIsInstance<KtNamedClassOrObjectSymbol>()
            )
        }
    }

    context(KtAnalysisSession)
    fun getJavaClassesByNameFilter(
        nameFilter: (Name) -> Boolean,
        psiFilter: (PsiClass) -> Boolean = { true }
    ): Sequence<KtNamedClassOrObjectSymbol> {
        val scope = analysisScope
        val names = buildSet<Name> {
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
        val scope = analysisScope
        val nameString = name.asString()

        return sequence {
            forEachNonKotlinCache { cache ->
                yieldAll(cache.getClassesByName(nameString, scope).iterator())
            }
        }
            .filter(psiFilter)
            .mapNotNull { it.getNamedClassSymbol() }
    }

    context(KtAnalysisSession)
    fun getKotlinCallableSymbolsByName(
        name: Name,
        psiFilter: (KtCallableDeclaration) -> Boolean = { true },
    ): Sequence<KtCallableSymbol> {
        val scope = analysisScope
        val nameString = name.asString()

        val values = SmartList<KtNamedDeclaration>()
        val processor = CancelableCollectFilterProcessor(values) {
            it is KtCallableDeclaration && psiFilter(it) && !it.isExpectDeclaration()
        }
        KotlinFunctionShortNameIndex.processElements(nameString, project, scope, processor)
        KotlinPropertyShortNameIndex.processElements(nameString, project, scope, processor)

        return sequence {
            for (callableDeclaration in values) {
                callableDeclaration.getSymbolOfTypeSafe<KtCallableSymbol>()?.let { yield(it) }
            }
            yieldAll(
                getResolveExtensionScopeWithTopLevelDeclarations().getCallableSymbols { it == name }
            )
        }
    }

    context(KtAnalysisSession)
    fun getJavaCallableSymbolsByName(
        name: Name,
        psiFilter: (PsiMember) -> Boolean = { true }
    ): Sequence<KtCallableSymbol> {
        val scope = analysisScope
        val nameString = name.asString()

        return sequence {
            forEachNonKotlinCache { cache -> yieldAll(cache.getMethodsByName(nameString, scope).iterator()) }
            forEachNonKotlinCache { cache -> yieldAll(cache.getFieldsByName(nameString, scope).iterator()) }
        }
            .filter(psiFilter)
            .mapNotNull { it.getCallableSymbol() }

    }

    private inline fun forEachNonKotlinCache(action: (cache: PsiShortNamesCache) -> Unit) {
        for (cache in PsiShortNamesCache.EP_NAME.getExtensions(project)) {
            if (cache::class.java.name == "org.jetbrains.kotlin.idea.caches.KotlinShortNamesCache") continue
            action(cache)
        }
    }

    private fun getShortName(fqName: String) = Name.identifier(fqName.substringAfterLast('.'))
}