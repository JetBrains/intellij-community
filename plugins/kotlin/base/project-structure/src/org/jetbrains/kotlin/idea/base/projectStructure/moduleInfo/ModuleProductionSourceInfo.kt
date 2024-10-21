// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.module.Module
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.caches.project.cacheByClassInvalidatingOnRootModifications
import org.jetbrains.kotlin.idea.base.facet.additionalVisibleModules
import org.jetbrains.kotlin.idea.base.facet.stableName
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinResolveScopeEnlarger
import org.jetbrains.kotlin.idea.base.projectStructure.productionSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.scope.ModuleSourcesScope
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.name.Name

@K1ModeProjectStructureApi
data class ModuleProductionSourceInfo internal constructor(
    override val module: Module
) : ModuleSourceInfoWithExpectedBy(forProduction = true) {

    override val name: Name
        get() = Name.special("<production sources for module ${module.name}>")

    override val stableName: Name by lazy { module.stableName }

    override fun keyForSdk(): KeyForSdks = KeyForSdks

    override val contentScope: GlobalSearchScope
        get() = KotlinResolveScopeEnlarger.enlargeScope(module.kotlinProductionSourceScope, module, isTestScope = false)

    private val Module.kotlinProductionSourceScope: GlobalSearchScope
        get() = ModuleSourcesScope.production(module)

    override fun modulesWhoseInternalsAreVisible(): Collection<ModuleInfo> {
        return module.cacheByClassInvalidatingOnRootModifications(KeyForModulesWhoseInternalsAreVisible::class.java) {
            module.additionalVisibleModules.mapNotNull { it.productionSourceInfo }
        }
    }

    protected object KeyForSdks

    private object KeyForModulesWhoseInternalsAreVisible
}