// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analyzer.*
import org.jetbrains.kotlin.descriptors.ModuleCapability
import org.jetbrains.kotlin.idea.base.platforms.isSharedNative
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinBaseProjectStructureBundle
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.konan.library.KONAN_STDLIB_NAME
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.*
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.NativePlatform
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.platform.konan.isNative
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

/**
 * Filter for dependencies on source modules.
 * This shall act as last line of defense for catastrophic misconfiguration of order entries.
 * Generally, we do trust Gradle/Import/Users to select only 'reasonable' dependencies for source modules.
 * However: Passing dependencies produced by one Kotlin backend into analysis of another platform might have unpredictable/unwanted
 * consequences and is forbidden under the implementations rules (even if users added those order entries themselves explicitly)
 */
internal interface SourceModuleDependenciesFilter {
    fun isSupportedDependency(dependency: IdeaModuleInfo): Boolean
}

@ApiStatus.Internal
class HmppSourceModuleDependencyFilter(private val dependeePlatform: TargetPlatform) : SourceModuleDependenciesFilter {
    data class KlibLibraryGist(val isStdlib: Boolean)

    private fun klibLibraryGistOrNull(info: IdeaModuleInfo): KlibLibraryGist? {
        return if (info is AbstractKlibLibraryInfo) KlibLibraryGist(isStdlib = info.libraryRoot.endsWith(KONAN_STDLIB_NAME))
        else null
    }

    override fun isSupportedDependency(dependency: IdeaModuleInfo): Boolean {
        /* Filter only acts on LibraryInfo */
        return if (dependency is LibraryInfo) {
            isSupportedDependency(dependency.platform, klibLibraryGistOrNull(dependency))
        } else true
    }

    fun isSupportedDependency(
        dependencyPlatform: TargetPlatform,
        klibLibraryGist: KlibLibraryGist? = null,
    ): Boolean {
        // HACK: allow depending on stdlib even if platforms do not match
        if (dependeePlatform.isNative() && klibLibraryGist != null && klibLibraryGist.isStdlib) return true

        val platformsWhichAreNotContainedInOther = dependeePlatform.componentPlatforms - dependencyPlatform.componentPlatforms
        if (platformsWhichAreNotContainedInOther.isEmpty()) return true

        // unspecifiedNativePlatform is effectively a wildcard for NativePlatform
        if (platformsWhichAreNotContainedInOther.all { it is NativePlatform } &&
            NativePlatforms.unspecifiedNativePlatform.componentPlatforms.single() in dependencyPlatform.componentPlatforms
        ) return true

        // Allow dependencies from any shared native to any other shared native platform.
        //  This will also include dependencies built by the commonizer with one or more missing targets
        //  The Kotlin Gradle Plugin will decide if the dependency is still used in that case.
        //  Since compiling metadata will be possible with this KLIB, the IDE also analyzes the code with it.
        if (dependeePlatform.isSharedNative() && klibLibraryGist != null && dependencyPlatform.isSharedNative()) return true

        return false
    }
}

@ApiStatus.Internal
class NonHmppSourceModuleDependenciesFilter(private val dependeePlatform: TargetPlatform) : SourceModuleDependenciesFilter {
    override fun isSupportedDependency(dependency: IdeaModuleInfo): Boolean {
        /* Filter only acts on LibraryInfo */
        return if (dependency is LibraryInfo) {
            isSupportedDependency(dependency.platform)
        } else true
    }

    private fun isSupportedDependency(dependencyPlatform: TargetPlatform): Boolean {
        return dependeePlatform.isJvm() && dependencyPlatform.isJvm() ||
                dependeePlatform.isJs() && dependencyPlatform.isJs() ||
                dependeePlatform.isNative() && dependencyPlatform.isNative() ||
                dependeePlatform.isCommon() && dependencyPlatform.isCommon()
    }
}

internal class NegatedModuleDependencyFilter(private val original: SourceModuleDependenciesFilter) : SourceModuleDependenciesFilter {
    override fun isSupportedDependency(dependency: IdeaModuleInfo): Boolean {
        return !original.isSupportedDependency(dependency)
    }
}