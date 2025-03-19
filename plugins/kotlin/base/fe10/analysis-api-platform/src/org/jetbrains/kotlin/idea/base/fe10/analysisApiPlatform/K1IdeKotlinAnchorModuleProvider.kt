// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.analysisApiPlatform

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.base.fe10.analysis.ResolutionAnchorCacheService
import org.jetbrains.kotlin.idea.base.analysisApiPlatform.IdeKotlinAnchorModuleProvider
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.toKaModule
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi

class K1IdeKotlinAnchorModuleProvider(val project: Project) : IdeKotlinAnchorModuleProvider {
    override fun getAnchorModule(libraryModule: KaLibraryModule): KaSourceModule? {
        @OptIn(K1ModeProjectStructureApi::class)
        val libraryInfo = libraryModule.moduleInfo as? LibraryInfo ?: return null
        return ResolutionAnchorCacheService.getInstance(project).resolutionAnchorsForLibraries[libraryInfo]?.toKaModule() as? KaSourceModule
    }

    override fun getAllAnchorModules(): Collection<KaSourceModule> =
        ResolutionAnchorCacheService.getInstance(project)
            .librariesForResolutionAnchors.keys
            .mapNotNull { it.toKaModule() as? KaSourceModule }


    @OptIn(K1ModeProjectStructureApi::class)
    override fun getAnchorLibraries(sourceModule: KaSourceModule): List<KaLibraryModule> {
        val sourceModuleInfo = sourceModule.moduleInfo as? ModuleSourceInfo ?: return emptyList()
        return ResolutionAnchorCacheService.getInstance(project)
            .librariesForResolutionAnchors[sourceModuleInfo]
            ?.map { it.toKaModule() as KaLibraryModule }
            ?: emptyList()
    }

    override fun getAllAnchorModulesIfComputed(): Collection<KaSourceModule>? {
        ThreadingAssertions.assertWriteAccess()
        val librariesForResolutionAnchorsIfComputed =
            ResolutionAnchorCacheService.getInstance(project).librariesForResolutionAnchorsIfComputed ?: return null
        return librariesForResolutionAnchorsIfComputed.keys
            .mapNotNull { it.toKaModule() as? KaSourceModule }
    }
}