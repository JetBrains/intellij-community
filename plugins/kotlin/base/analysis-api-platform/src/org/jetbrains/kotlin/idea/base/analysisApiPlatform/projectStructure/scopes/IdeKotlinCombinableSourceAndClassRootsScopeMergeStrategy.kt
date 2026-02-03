// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform.projectStructure.scopes

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinGlobalSearchScopeMergeStrategy
import org.jetbrains.kotlin.idea.base.projectStructure.scope.CombinableSourceAndClassRootsScope
import org.jetbrains.kotlin.idea.base.projectStructure.scope.CombinedSourceAndClassRootsScope
import kotlin.reflect.KClass

internal class IdeKotlinCombinableSourceAndClassRootsScopeMergeStrategy(
    private val project: Project,
): KotlinGlobalSearchScopeMergeStrategy<CombinableSourceAndClassRootsScope> {
    override val targetType: KClass<CombinableSourceAndClassRootsScope> = CombinableSourceAndClassRootsScope::class

    override fun uniteScopes(scopes: List<CombinableSourceAndClassRootsScope>): List<GlobalSearchScope> {
        @Suppress("UNCHECKED_CAST")
        return when {
            scopes.size <= 1 -> return scopes as List<GlobalSearchScope>
            else -> listOf(CombinedSourceAndClassRootsScope.Companion.create(scopes, project))
        }
    }
}
