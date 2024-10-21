// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.analysisApiPlatform

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.base.fe10.analysis.ResolutionAnchorCacheService
import org.jetbrains.kotlin.idea.base.analysisApiPlatform.IdeKotlinModuleDependentsProvider
import org.jetbrains.kotlin.idea.base.projectStructure.KtLibraryModuleByModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryDependenciesCache
import org.jetbrains.kotlin.idea.base.projectStructure.LibraryUsageIndex
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.toKaModule
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProductionOrTest
import org.jetbrains.kotlin.idea.base.projectStructure.util.getTransitiveLibraryDependencyInfos
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi

internal class K1IdeKotlinModuleDependentsProvider(project: Project) : IdeKotlinModuleDependentsProvider(project) {
    @OptIn(K1ModeProjectStructureApi::class)
    override fun addAnchorModuleDependents(
        module: KaSourceModule,
        to: MutableSet<KaModule>
    ) {
        val moduleInfo = module.moduleInfo as? ModuleSourceInfo ?: return

        // If `module` is an anchor module, it has library dependents in the form of anchoring libraries. See
        // `ResolutionAnchorCacheService` for additional documentation.
        val anchoringLibraries = ResolutionAnchorCacheService.getInstance(project).librariesForResolutionAnchors[moduleInfo] ?: return

        // Because dependency relationships between libraries aren't supported by the project model (as noted in
        // `ResolutionAnchorCacheService`), library dependencies are approximated by the following relationship: If a module `M1` depends on
        // two libraries `L1` and `L2`, `L1` depends on `L2` and `L2` depends on `L1` (`L1 <--> L2`). This does not apply without
        // restriction for multi-platform projects. However, anchor module usage is strictly limited to the `intellij` project, which is not
        // a multi-platform project. Because the approximate library dependencies are bidirectional, library dependencies are also library
        // dependents, and we can simply use `getTransitiveLibraryDependencyInfos`.
        //
        // Because anchor modules are rare and `getTransitiveDependents` already caches dependents as a whole, there is currently no need to
        // cache these transitive library dependencies.
        LibraryDependenciesCache.getInstance(project)
            .getTransitiveLibraryDependencyInfos(anchoringLibraries)
            .forEach { libraryInfo ->
                to.add(libraryInfo.toKaModule())
                to.add(libraryInfo.sourcesModuleInfo.toKaModule())
            }
    }

    override fun getDirectDependentsForLibraryNonSdkModule(module: KaLibraryModule): Set<KaModule> {
        require(module is KtLibraryModuleByModuleInfo)

        return project.service<LibraryUsageIndex>()
            .getDependentModules(module.libraryInfo)
            .mapNotNullTo(mutableSetOf()) { it.toKaSourceModuleForProductionOrTest() }
    }

}