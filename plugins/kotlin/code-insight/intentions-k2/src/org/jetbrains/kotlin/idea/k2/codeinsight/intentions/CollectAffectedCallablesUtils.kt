// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.OverridingMethodsSearch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allOverriddenSymbolsWithSelf
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ConvertFunctionToPropertyAndViceVersaUtils.isOverride
import org.jetbrains.kotlin.idea.search.ExpectActualUtils
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.actualsForExpect
import org.jetbrains.kotlin.idea.search.declarationsSearch.forEachOverridingElement
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration

@ApiStatus.Internal
object CollectAffectedCallablesUtils {

    fun KaSession.getAffectedCallables(callableSymbol: KaCallableSymbol): Collection<PsiElement> {
        val symbolsForChange = callableSymbol.allOverriddenSymbolsWithSelf.filter { isOverride(it) }
        val results = hashSetOf<PsiElement>()
        for (symbol in symbolsForChange) {
            val declaration = symbol.psi ?: continue
            collectAffectedCallables(declaration, results)
        }
        return results
    }

    private fun KaSession.collectAffectedCallables(
        declaration: PsiElement,
        results: MutableCollection<PsiElement>,
    ) {
        if (!results.add(declaration)) return
        if (declaration is KtDeclaration) {
            for (it in declaration.actualsForExpect()) {
                collectAffectedCallables(it, results)
            }
            ExpectActualUtils.liftToExpect(declaration)?.let { collectAffectedCallables(it, results) }

            if (declaration !is KtCallableDeclaration) return
            declaration.forEachOverridingElement { _, overridingElement ->
                results += overridingElement.namedUnwrappedElement ?: overridingElement
                true
            }
        } else {
            for (psiMethod in declaration.toLightMethods()) {
                OverridingMethodsSearch.search(psiMethod).findAll().forEach {
                    results += it.namedUnwrappedElement ?: it
                }
            }
        }
    }
}
