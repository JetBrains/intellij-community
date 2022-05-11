// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.facet.isMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.isNewMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.sourceType as sourceTypeNew
import org.jetbrains.kotlin.idea.base.facet.implementingModules as implementingModulesNew
import org.jetbrains.kotlin.idea.caches.project.SourceType.PRODUCTION
import org.jetbrains.kotlin.idea.caches.project.SourceType.TEST
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.konan.NativePlatform
import org.jetbrains.kotlin.platform.konan.NativePlatformUnspecifiedTarget
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
    "Use 'org.jetbrains.kotlin.idea.base.facet.sourceType' instead.",
    ReplaceWith("sourceType", imports = ["org.jetbrains.kotlin.idea.base.facet"]),
    level = DeprecationLevel.ERROR
)
@Suppress("unused")
val Module.sourceType: SourceType?
    get() = sourceTypeNew

@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.facet.isMultiPlatformModule' instead.",
    ReplaceWith("isMultiPlatformModule", imports = ["org.jetbrains.kotlin.idea.base.facet"]),
    level = DeprecationLevel.ERROR
)
@Suppress("unused")
val Module.isMPPModule: Boolean
    get() = isMultiPlatformModule

@Deprecated(
    "Use 'org.jetbrains.kotlin.idea.base.facet.implementingModules' instead.",
    ReplaceWith("implementingModules", imports = ["org.jetbrains.kotlin.idea.base.facet"]),
    level = DeprecationLevel.ERROR
)
@Suppress("unused")
val Module.implementingModules: List<Module>
    get() = implementingModulesNew

val ModuleDescriptor.implementingDescriptors: List<ModuleDescriptor>
    get() {
        val moduleInfo = getCapability(ModuleInfo.Capability)
        if (moduleInfo is PlatformModuleInfo) {
            return listOf(this)
        }
        val moduleSourceInfo = moduleInfo as? ModuleSourceInfo ?: return emptyList()
        val implementingModuleInfos = moduleSourceInfo.module.implementingModules.mapNotNull { it.toInfo(moduleSourceInfo.sourceType) }
        return implementingModuleInfos.mapNotNull { it.toDescriptor() }
    }

val ModuleDescriptor.allImplementingDescriptors: Collection<ModuleDescriptor>
    get() = implementingDescriptors.closure(preserveOrder = true) { it.implementingDescriptors }

fun Module.toInfo(type: SourceType): ModuleSourceInfo? = when (type) {
    PRODUCTION -> productionSourceInfo()
    TEST -> testSourceInfo()
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

fun Module.toDescriptor() = (productionSourceInfo() ?: testSourceInfo())?.toDescriptor()

fun ModuleSourceInfo.toDescriptor() = KotlinCacheService.getInstance(module.project)
    .getResolutionFacadeByModuleInfo(this, platform)?.moduleDescriptor

fun PsiElement.getPlatformModuleInfo(desiredPlatform: TargetPlatform): PlatformModuleInfo? {
    assert(!desiredPlatform.isCommon()) { "Platform module cannot have Common platform" }
    val moduleInfo = getNullableModuleInfo() as? ModuleSourceInfo ?: return null
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
            val correspondingImplementingModule = moduleInfo.module.implementingModules.map { it.toInfo(moduleInfo.sourceType) }
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

internal fun TargetPlatform.isSharedNative(): Boolean {
    if (this.componentPlatforms.all { it is NativePlatform }) {
        if (this.contains(NativePlatformUnspecifiedTarget)) return true
        return this.componentPlatforms.size > 1
    }
    return false
}
