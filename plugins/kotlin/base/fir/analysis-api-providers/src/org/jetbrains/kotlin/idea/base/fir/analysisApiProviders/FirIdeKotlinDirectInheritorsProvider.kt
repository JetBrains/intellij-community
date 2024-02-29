// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.providers.KotlinDirectInheritorsProvider
import org.jetbrains.kotlin.idea.searching.inheritors.DirectKotlinClassInheritorsSearch
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject

class FirIdeKotlinDirectInheritorsProvider : KotlinDirectInheritorsProvider {
    override fun getDirectKotlinInheritors(
        ktClass: KtClass,
        scope: GlobalSearchScope,
        includeLocalInheritors: Boolean
    ): Iterable<KtClassOrObject> {
        val searchParameters = DirectKotlinClassInheritorsSearch.SearchParameters(
            ktClass = ktClass,
            searchScope = scope,
            includeLocal = includeLocalInheritors,
        )

        return DirectKotlinClassInheritorsSearch.search(searchParameters).filterIsInstance<KtClassOrObject>().toList()
    }
}
