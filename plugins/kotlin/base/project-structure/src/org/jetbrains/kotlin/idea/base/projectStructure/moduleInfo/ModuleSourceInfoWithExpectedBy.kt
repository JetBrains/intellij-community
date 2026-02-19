// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.kotlin.caches.project.cacheByClassInvalidatingOnRootModifications
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.base.facet.implementedModules
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleDependencyCollector
import org.jetbrains.kotlin.idea.base.projectStructure.productionSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.testSourceInfo
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi

@K1ModeProjectStructureApi
sealed class ModuleSourceInfoWithExpectedBy(private val forProduction: Boolean) : ModuleSourceInfo {
    override val expectedBy: List<ModuleSourceInfo>
        get() {
            val expectedByModules = module.implementedModules
            return expectedByModules.mapNotNull { if (forProduction) it.productionSourceInfo else it.testSourceInfo }
        }

    override fun dependencies(): List<IdeaModuleInfo> = module.cacheByClassInvalidatingOnRootModifications(this::class.java) {
        val sourceRootType = if (forProduction) SourceKotlinRootType else TestSourceKotlinRootType
        ModuleDependencyCollector.getInstance(module.project)
            .collectModuleDependencies(module, platform, sourceRootType, includeExportedDependencies = true)
            .toList()
    }

    final override fun sdk(): Sdk? = module.cacheByClassInvalidatingOnRootModifications(keyForSdk()::class.java) {
        super.sdk()
    }

    protected abstract fun keyForSdk(): Any

}