// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.config.KotlinSourceRootType
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.facet.isMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.isNewMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.kotlinSourceRootType
import org.jetbrains.kotlin.idea.base.projectStructure.kotlinSourceRootType
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.PlatformModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfoOrNull
import org.jetbrains.kotlin.idea.base.projectStructure.productionSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.testSourceInfo
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.types.typeUtil.closure

@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.facet.isNewMultiPlatformModule' instead.",
    ReplaceWith("this.isNewMultiPlatformModule", imports = ["org.jetbrains.kotlin.idea.base.facet"]),
    level = DeprecationLevel.ERROR
)
@Suppress("unused")
val Module.isNewMPPModule: Boolean
    get() = isNewMultiPlatformModule

@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.facet.kotlinSourceRootType' instead.",
    ReplaceWith("sourceType", imports = ["org.jetbrains.kotlin.idea.base.facet"]),
    level = DeprecationLevel.ERROR
)
@Suppress("unused")
val Module.sourceType: SourceType?
    get() = when (kotlinSourceRootType) {
        TestSourceKotlinRootType -> @Suppress("DEPRECATION") SourceType.TEST
        SourceKotlinRootType -> @Suppress("DEPRECATION") SourceType.PRODUCTION
        else -> null
    }

@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.facet.isMultiPlatformModule' instead.",
    ReplaceWith("isMultiPlatformModule", imports = ["org.jetbrains.kotlin.idea.base.facet"]),
    level = DeprecationLevel.ERROR
)
@Suppress("unused")
val Module.isMPPModule: Boolean
    get() = isMultiPlatformModule

val ModuleDescriptor.implementingDescriptors: List<ModuleDescriptor>
    get() {
        val moduleInfo = getCapability(ModuleInfo.Capability)
        if (moduleInfo is PlatformModuleInfo) {
            return listOf(this)
        }

        val moduleSourceInfo = moduleInfo as? ModuleSourceInfo ?: return emptyList()
        val implementingModuleInfos = moduleSourceInfo.module.implementingModules
            .mapNotNull { module ->
                val sourceRootType = moduleSourceInfo.kotlinSourceRootType ?: return@mapNotNull null
                module.getModuleInfo(sourceRootType)
            }

        return implementingModuleInfos.mapNotNull { it.toDescriptor() }
    }

val ModuleDescriptor.allImplementingDescriptors: Collection<ModuleDescriptor>
    get() = implementingDescriptors.closure(preserveOrder = true) { it.implementingDescriptors }

fun Module.getModuleInfo(sourceRootType: KotlinSourceRootType): ModuleSourceInfo? {
    return when (sourceRootType) {
        SourceKotlinRootType -> productionSourceInfo
        TestSourceKotlinRootType -> testSourceInfo
    }
}

/**
 * This function returns immediate parents in dependsOn graph
 */
val ModuleDescriptor.implementedDescriptors: List<ModuleDescriptor>
    get() {
        val moduleInfo = getCapability(ModuleInfo.Capability)
        if (moduleInfo is PlatformModuleInfo) return listOf(this)

        val moduleSourceInfo = moduleInfo as? ModuleSourceInfo ?: return emptyList()

        return moduleSourceInfo.expectedBy.mapNotNull { it.toDescriptor() }
    }

fun Module.toDescriptor() = (productionSourceInfo ?: testSourceInfo)?.toDescriptor()

fun ModuleSourceInfo.toDescriptor() = KotlinCacheService.getInstance(module.project)
    .getResolutionFacadeByModuleInfo(this, platform)?.moduleDescriptor

fun PsiElement.getPlatformModuleInfo(desiredPlatform: TargetPlatform): PlatformModuleInfo? {
    assert(!desiredPlatform.isCommon()) { "Platform module cannot have Common platform" }
    val moduleInfo = this.moduleInfoOrNull as? ModuleSourceInfo ?: return null
    return doGetPlatformModuleInfo(moduleInfo, desiredPlatform)
}

fun getPlatformModuleInfo(moduleInfo: ModuleSourceInfo, desiredPlatform: TargetPlatform): PlatformModuleInfo? {
    assert(!desiredPlatform.isCommon()) { "Platform module cannot have Common platform" }
    return doGetPlatformModuleInfo(moduleInfo, desiredPlatform)
}

private fun doGetPlatformModuleInfo(moduleInfo: ModuleSourceInfo, desiredPlatform: TargetPlatform): PlatformModuleInfo? {
    val platform = moduleInfo.platform
    return when {
        platform.isCommon() -> {
            val correspondingImplementingModule = moduleInfo.module.implementingModules
                .map { module ->
                    val sourceRootType = moduleInfo.kotlinSourceRootType ?: return@map null
                    module.getModuleInfo(sourceRootType)
                }
                .firstOrNull { it?.platform == desiredPlatform } ?: return null

            PlatformModuleInfo(correspondingImplementingModule, correspondingImplementingModule.expectedBy)
        }
        platform == desiredPlatform -> {
            val expectedBy = moduleInfo.expectedBy.takeIf { it.isNotEmpty() } ?: return null
            PlatformModuleInfo(moduleInfo, expectedBy)
        }
        else -> null
    }
}