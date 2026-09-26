// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.source

import com.intellij.openapi.module.Module
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.workspaceModel.ide.legacyBridge.findModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.idea.base.facet.stableName
import org.jetbrains.kotlin.idea.base.fir.projectStructure.modules.KaEntityBasedModule
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleWithKind
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.projectStructure.scope.ModuleSourcesScope
import org.jetbrains.kotlin.idea.project.ModulePlatformCache
import org.jetbrains.kotlin.platform.TargetPlatform

internal abstract class KaSourceModuleBase() : KaEntityBasedModule<ModuleEntity, ModuleId>(), KaSourceModuleWithKind {
    override val name: String get() = entityId.name

    internal val module: Module
        get() = entity.findModule(currentSnapshot)
            ?: error("Could not find module $entityId")

    override val languageVersionSettings: LanguageVersionSettings
        get() = module.languageVersionSettings

    override val directRegularDependencies: List<KaModule> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        KaSourceModuleDependenciesProvider.getInstance(project)
            .getDirectRegularDependencies(
                this,
                // TODO KT-74301
                // This is how the old K2 implementation handled it,
                // and it should be discussed if `directFriendDependencies` and `directDependsOnDependencies` should be here.
                directFriendDependencies + directDependsOnDependencies
            )
    }

    override val directDependsOnDependencies: List<KaModule> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        KaSourceModuleDependenciesProvider.getInstance(project).getDirectDependsOnDependencies(this)
    }

    override val directFriendDependencies: List<KaModule> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        KaSourceModuleDependenciesProvider.getInstance(project).getDirectFriendDependencies(this)
    }

    // When changing the type of the base content scope, please update `K2IdeKotlinModuleInformationProvider.isEmpty`.
    override val baseContentScope: GlobalSearchScope
        get() = when (kind) {
            KaSourceModuleKind.PRODUCTION -> {
                ModuleSourcesScope.production(module)
            }

            KaSourceModuleKind.TEST -> {
                ModuleSourcesScope.tests(module)
            }
        }

    override val targetPlatform: TargetPlatform
        get() = ModulePlatformCache.getInstance(project)[module]

    override val stableModuleName: String
        get() = module.stableName.asString()

    override fun toString(): String {
        return super.toString() + ", kind=${kind}"
    }
}
