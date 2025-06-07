// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform.projectStructure.scopes

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopeUtil
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinGlobalSearchScopeMergeStrategy
import kotlin.reflect.KClass

/**
 * A [KotlinGlobalSearchScopeMergeStrategy] introduced to flatten nested union scopes. As [com.intellij.psi.search.UnionScope] is
 * package-private, the strategy has to target [GlobalSearchScope].
 */
internal class IdeKotlinUnionScopeMergeStrategy : KotlinGlobalSearchScopeMergeStrategy<GlobalSearchScope> {
    override val targetType: KClass<GlobalSearchScope> = GlobalSearchScope::class

    override fun uniteScopes(scopes: List<GlobalSearchScope>): List<GlobalSearchScope> {
        return scopes.flatMapTo(mutableSetOf()) { scope -> GlobalSearchScopeUtil.flattenUnionScope(scope) }.toList()
    }
}
