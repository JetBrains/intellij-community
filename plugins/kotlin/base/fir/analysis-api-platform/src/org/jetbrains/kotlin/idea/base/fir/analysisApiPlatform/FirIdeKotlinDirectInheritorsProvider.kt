// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDirectInheritorsProvider
import org.jetbrains.kotlin.analysis.decompiler.konan.KlibMetaFileType
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.searching.inheritors.DirectKotlinClassInheritorsSearch
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject

class FirIdeKotlinDirectInheritorsProvider : KotlinDirectInheritorsProvider {
    override fun getDirectKotlinInheritors(
        ktClass: KtClass,
        scope: GlobalSearchScope,
        includeLocalInheritors: Boolean
    ): Iterable<KtClassOrObject> {
        // Avoid search in Java files to avoid usage of light classes during the search (see KT-68603). The scope is potentially
        // inefficient (KTIJ-20095), but heavy use of the direct inheritors provider is currently not expected.
        val kotlinOnlyScope = GlobalSearchScope.getScopeRestrictedByFileTypes(
            scope,
            KotlinFileType.INSTANCE,
            JavaClassFileType.INSTANCE,
            KotlinBuiltInFileType,
            KlibMetaFileType,
        )

        val searchParameters = DirectKotlinClassInheritorsSearch.SearchParameters(
            ktClass = ktClass,
            searchScope = kotlinOnlyScope,
            includeLocal = includeLocalInheritors,
        )

        return DirectKotlinClassInheritorsSearch.search(searchParameters).asIterable().filterIsInstance<KtClassOrObject>().toList()
    }
}
