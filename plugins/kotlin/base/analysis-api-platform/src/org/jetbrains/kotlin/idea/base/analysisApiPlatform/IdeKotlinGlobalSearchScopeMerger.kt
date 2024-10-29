// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinGlobalSearchScopeMerger
import org.jetbrains.kotlin.idea.base.projectStructure.scope.CombinableSourceAndClassRootsScope
import org.jetbrains.kotlin.idea.base.projectStructure.scope.CombinedSourceAndClassRootsScope
import org.jetbrains.kotlin.utils.addToStdlib.partitionIsInstance

internal class IdeKotlinGlobalSearchScopeMerger(private val project: Project) : KotlinGlobalSearchScopeMerger {
    override fun union(scopes: Collection<GlobalSearchScope>): GlobalSearchScope {
        if (scopes.isEmpty()) {
            return GlobalSearchScope.EMPTY_SCOPE
        }

        if (scopes.size < 2 || scopes.count { it is CombinableSourceAndClassRootsScope } < 2) {
            return GlobalSearchScope.union(scopes)
        }

        val (combinableScopes, otherScopes) = scopes.partitionIsInstance<_, CombinableSourceAndClassRootsScope>()

        return GlobalSearchScope.union(listOf(CombinedSourceAndClassRootsScope.create(combinableScopes, project)) + otherScopes)
    }
}
