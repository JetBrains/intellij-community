// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hierarchy

import com.intellij.psi.PsiElement
import com.intellij.psi.search.PsiSearchScopeUtil
import com.intellij.psi.search.SearchScope
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.actualsForExpect
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.expectDeclarationIfAny
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isActualDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import com.intellij.openapi.module.Module

internal fun collectInheritors(
    klass: PsiElement,
    baseModule: Module? = null,
    searchScope: SearchScope,
    baseScope: SearchScope
): List<PsiElement> = if (klass is KtClassOrObject) {
    val isExpectDeclaration = klass.isExpectDeclaration()
    val isActualDeclaration = klass.isActualDeclaration()
    val expectedClassOrObject = klass.expectDeclarationIfAny() as? KtClassOrObject ?: klass
    val actuals = if (isExpectDeclaration) expectedClassOrObject.actualsForExpect() else emptyList()
    val withCommonScope =
        if (klass == expectedClassOrObject) {
            searchScope
        } else {
            val expectClassScope = expectedClassOrObject.useScope.intersectWith(klass.resolveScope)
            searchScope.union(expectClassScope.intersectWith(baseScope))
        }
    actuals.filter { baseModule == null || PsiSearchScopeUtil.isInScope(baseModule.moduleWithDependentsScope, it) } +
            KotlinFindUsagesSupport.searchInheritors(expectedClassOrObject, withCommonScope, searchDeeply = false)
                .filter { inheritor ->
                    if (isExpectDeclaration) {
                        // skip inheritors of the platform classes, they are included in the next level of `actuals` list
                        val scope = inheritor.module?.moduleWithDependenciesScope
                        scope != null && actuals.none {
                            PsiSearchScopeUtil.isInScope(scope, it)
                        }
                    } else if (isActualDeclaration) {
                        // do not include expect declarations, they should be actualized in platforms
                        (inheritor as? KtDeclaration)?.isExpectDeclaration() != true
                    } else {
                        true
                    }
                }.toList()
} else {
    KotlinFindUsagesSupport.searchInheritors(klass, searchScope, searchDeeply = false).toList()
}
