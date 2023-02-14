// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiProviders

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.allDirectDependencies
import org.jetbrains.kotlin.analysis.providers.KotlinResolutionScopeProvider

internal class IdeKotlinByModulesResolutionScopeProvider : KotlinResolutionScopeProvider() {
    override fun getResolutionScope(module: KtModule): GlobalSearchScope {
        val allModules = buildList {
            add(module)
            addAll(module.allDirectDependencies())
        }
        return GlobalSearchScope.union(allModules.map { it.contentScope })
    }
}