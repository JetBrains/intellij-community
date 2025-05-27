// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform

import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinModuleInformationProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.scope.ModuleSourcesScope

internal class K2IdeKotlinModuleInformationProvider : KotlinModuleInformationProvider {
    override fun isEmpty(module: KaModule): Boolean? {
        if (module !is KaSourceModule) return null

        // Note: If we have a source module with a different content scope than `ModuleSourcesScope`, we cannot treat the module as empty,
        // as content scope refinement may have added any number of enlargement scopes. This would make the module non-empty despite its
        // possibly empty base content scope.
        val moduleSourcesScope = module.contentScope as? ModuleSourcesScope ?: return null
        return moduleSourcesScope.isEmpty()
    }
}
