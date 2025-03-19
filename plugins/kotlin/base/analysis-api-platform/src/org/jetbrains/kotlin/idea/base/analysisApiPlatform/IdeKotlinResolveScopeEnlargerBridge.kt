// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinContentScopeRefiner
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinResolveScopeEnlarger
import org.jetbrains.kotlin.idea.base.projectStructure.openapiModule
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleKind

/** Bridges FE1.0 [KotlinResolveScopeEnlarger] to AA [KotlinContentScopeRefiner]. */
class IdeKotlinResolveScopeEnlargerBridge : KotlinContentScopeRefiner {
    override fun getEnlargementScopes(module: KaModule): List<GlobalSearchScope> {
        if (module !is KaSourceModule) return emptyList()

        return listOf(KotlinResolveScopeEnlarger.enlargeScope(
            GlobalSearchScope.EMPTY_SCOPE,
            module.openapiModule,
            isTestScope = module.sourceModuleKind == KaSourceModuleKind.TEST,
        ))
    }
}
