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
import org.jetbrains.kotlin.idea.stubindex.KotlinFunctionShortNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinPropertyShortNameIndex
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject

class KtSymbolFromIndexProvider(private val project: Project) {
    context(KtAnalysisSession)
    fun getClassesByName(
        name: Name,
        kotlinPsiFilter: (KtClassOrObject) -> Boolean = { true },
        nonKotlinPsiFilter: (PsiClass) -> Boolean = { true }
    ): Sequence<KtNamedClassOrObjectSymbol> {
        val scope = analysisScope
        val nameString = name.asString()

        val kotlinClasses = KotlinClassShortNameIndex[nameString, project, scope]
            .asSequence()
            .filter(kotlinPsiFilter)
            .mapNotNull { it.getNamedClassOrObjectSymbol() }

        val javaClasses = sequence {
            forEachNonKotlinCache { cache ->
                yieldAll(cache.getClassesByName(nameString, scope).iterator())
            }
        }
            .filter(nonKotlinPsiFilter)
            .mapNotNull { it.getNamedClassSymbol() }
        return kotlinClasses + javaClasses
    }

    context(KtAnalysisSession)
    fun getCallableSymbolsByName(
        name: Name,
        kotlinPsiFilter: (KtCallableDeclaration) -> Boolean = { true },
        nonKotlinPsiFilter: (PsiMember) -> Boolean = { true }
    ): Sequence<KtCallableSymbol> {
        val scope = analysisScope
        val nameString = name.asString()

        val kotlinCallables = sequence {
            yieldAll(KotlinFunctionShortNameIndex[nameString, project, scope])
            yieldAll(KotlinPropertyShortNameIndex[nameString, project, scope])
        }
            .onEach { ProgressManager.checkCanceled() }
            .filter { it is KtCallableDeclaration && kotlinPsiFilter(it) }
            .mapNotNull { it.getSymbolOfTypeSafe<KtCallableSymbol>() }

        val javaCallables = sequence {
            forEachNonKotlinCache { cache -> yieldAll(cache.getMethodsByName(nameString, analysisScope).iterator()) }
            forEachNonKotlinCache { cache -> yieldAll(cache.getFieldsByName(nameString, analysisScope).iterator()) }
        }
            .filter(nonKotlinPsiFilter)
            .mapNotNull { it.getCallableSymbol() }

        return kotlinCallables + javaCallables
    }

    private inline fun SequenceScope<*>.forEachNonKotlinCache(action: SequenceScope<*>.(cache: PsiShortNamesCache) -> Unit) {
        for (cache in PsiShortNamesCache.EP_NAME.getExtensions(project)) {
            if (cache::class.java.name == "org.jetbrains.kotlin.idea.caches.KotlinShortNamesCache") continue
            action(cache)
        }
    }
}