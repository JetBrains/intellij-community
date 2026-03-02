// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analyzer.CombinedModuleInfo
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.TrackableModuleInfo
import org.jetbrains.kotlin.descriptors.ModuleCapability
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinBaseProjectStructureBundle
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.idea.base.projectStructure.kmp.NonHmppSourceModuleDependenciesFilter
import org.jetbrains.kotlin.idea.base.projectStructure.kmp.SourceModuleDependenciesFilter
import org.jetbrains.kotlin.idea.base.projectStructure.kmp.SourceModuleDependenciesFilterCandidate
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.konan.library.KONAN_STDLIB_NAME
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices

@K1ModeProjectStructureApi
data class PlatformModuleInfo(
    override val platformModule: ModuleSourceInfo,
    private val commonModules: List<ModuleSourceInfo> // NOTE: usually contains a single element for current implementation
) : IdeaModuleInfo, CombinedModuleInfo, TrackableModuleInfo {
    override val capabilities: Map<ModuleCapability<*>, Any?>
        get() = platformModule.capabilities

    override val contentScope get() = GlobalSearchScope.union(containedModules.map { it.contentScope }.toTypedArray())

    override val containedModules: List<ModuleSourceInfo> = listOf(platformModule) + commonModules

    override val project: Project
        get() = platformModule.module.project

    override val platform: TargetPlatform
        get() = platformModule.platform

    override val moduleOrigin: ModuleOrigin
        get() = platformModule.moduleOrigin

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = platform.findAnalyzerServices(platformModule.module.project)

    override fun dependencies() = platformModule.dependencies()
        // This is needed for cases when we create PlatformModuleInfo in Kotlin Multiplatform Analysis Mode is set to COMPOSITE, see
        // KotlinCacheService.getResolutionFacadeWithForcedPlatform.
        // For SEPARATE-mode, this filter will be executed in getSourceModuleDependencies.kt anyway, so it's essentially a no-op
        .filter { NonHmppSourceModuleDependenciesFilter(platformModule.platform).isSupportedDependency(it) }

    override val expectedBy: List<ModuleInfo>
        get() = platformModule.expectedBy

    override fun modulesWhoseInternalsAreVisible() = containedModules.flatMap { it.modulesWhoseInternalsAreVisible() }

    override val name: Name = Name.special("<Platform module ${platformModule.name} including ${commonModules.map { it.name }}>")

    override val displayedName: String
        get() = KotlinBaseProjectStructureBundle.message(
            "platform.module.0.including.1",
            platformModule.displayedName,
            commonModules.map { it.displayedName }
        )

    override fun createModificationTracker() = platformModule.createModificationTracker()
}

@ApiStatus.Internal
@K1ModeProjectStructureApi
fun SourceModuleDependenciesFilter.isSupportedDependency(moduleInfo: IdeaModuleInfo): Boolean {
    val candidate = when (moduleInfo) {
        is AbstractKlibLibraryInfo -> SourceModuleDependenciesFilterCandidate.KlibLibraryDependency(
            moduleInfo.platform,
            isNativeStdlib =  moduleInfo.libraryRoot.endsWith(KONAN_STDLIB_NAME),
        )
        is LibraryInfo -> SourceModuleDependenciesFilterCandidate.NonKlibLibraryDependency(moduleInfo.platform)
        else -> SourceModuleDependenciesFilterCandidate.ModuleDependency(moduleInfo.platform)
    }
    return isSupportedDependency(candidate)
}