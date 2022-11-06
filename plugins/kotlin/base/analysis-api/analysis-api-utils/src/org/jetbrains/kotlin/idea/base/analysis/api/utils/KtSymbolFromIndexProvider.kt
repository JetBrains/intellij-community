// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis.api.utils

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMember
import com.intellij.psi.search.PsiShortNamesCache
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.getSymbolOfTypeSafe
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinFunctionShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinPropertyShortNameIndex
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.idea.base.psi.isExpectDeclaration
import org.jetbrains.kotlin.platform.isCommon

class KtSymbolFromIndexProvider(private val project: Project) {
    context(KtAnalysisSession)
    fun getKotlinClassesByName(
        name: Name,
        psiFilter: (KtClassOrObject) -> Boolean = { true },
    ): Sequence<KtNamedClassOrObjectSymbol> {
        val scope = analysisScope
        val isCommon = useSiteModule.platform.isCommon()
        return KotlinClassShortNameIndex[name.asString(), project, scope]
            .asSequence()
            .filter { ktClass -> isCommon || !ktClass.isExpectDeclaration() }
            .filter (psiFilter)
            .mapNotNull { it.getNamedClassOrObjectSymbol() }
    }

    context(KtAnalysisSession)
    fun getKotlinClassesByNameFilter(
        nameFilter: (Name) -> Boolean,
        psiFilter: (KtClassOrObject) -> Boolean = { true },
    ): Sequence<KtNamedClassOrObjectSymbol> {
        val scope = analysisScope
        val isCommon = useSiteModule.platform.isCommon()
        val index = KotlinFullClassNameIndex
        return index.getAllKeys(project).asSequence()
            .filter { fqName -> nameFilter(getShortName(fqName)) }
            .flatMap { fqName -> index[fqName, project, scope] }
            .filter { ktClass -> isCommon || !ktClass.isExpectDeclaration() }
            .filter (psiFilter)
            .mapNotNull { it.getNamedClassOrObjectSymbol() }
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

        return sequence {
            yieldAll(KotlinFunctionShortNameIndex[nameString, project, scope])
            yieldAll(KotlinPropertyShortNameIndex[nameString, project, scope])
        }
            .onEach { ProgressManager.checkCanceled() }
            .filter { it is KtCallableDeclaration && psiFilter(it) && !it.isExpectDeclaration()}
            .mapNotNull { it.getSymbolOfTypeSafe<KtCallableSymbol>() }
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