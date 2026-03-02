// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure


import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analyzer.LanguageSettingsProvider
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.config.KotlinSourceRootType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.base.facet.isNewMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.kotlinSourceRootType
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleProductionSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfoWithExpectedBy
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleTestSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.psi.UserDataProperty

val KaModule.moduleInfo: IdeaModuleInfo
    get() {
        require(this is KtModuleByModuleInfoBase)
        return ideaModuleInfo
    }


val ModuleInfo.kotlinSourceRootType: KotlinSourceRootType?
    get() = when (this) {
        is ModuleProductionSourceInfo -> SourceKotlinRootType
        is ModuleTestSourceInfo -> TestSourceKotlinRootType
        else -> null
    }


val Module.productionSourceInfo: ModuleProductionSourceInfo?
    get() {
        val hasProductionRoots = hasRootsOfType(
            setOf(
                JavaSourceRootType.SOURCE,
                SourceKotlinRootType
            )
        ) || (isNewMultiPlatformModule && kotlinSourceRootType == SourceKotlinRootType)

        return if (hasProductionRoots) ModuleProductionSourceInfo(this) else null
    }


val Module.testSourceInfo: ModuleTestSourceInfo?
    get() {
        val hasTestRoots = hasRootsOfType(
            setOf(
                JavaSourceRootType.TEST_SOURCE,
                TestSourceKotlinRootType
            )
        ) || (isNewMultiPlatformModule && kotlinSourceRootType == TestSourceKotlinRootType)

        return if (hasTestRoots) ModuleTestSourceInfo(this) else null
    }


fun Module.asSourceInfo(sourceRootType: KotlinSourceRootType?): ModuleSourceInfoWithExpectedBy? = when (sourceRootType) {
    SourceKotlinRootType -> ModuleProductionSourceInfo(this)
    TestSourceKotlinRootType -> ModuleTestSourceInfo(this)
    else -> null
}

val Module.sourceModuleInfos: List<ModuleSourceInfo>
    get() = listOfNotNull(testSourceInfo, productionSourceInfo)

private fun Module.hasRootsOfType(rootTypes: Set<JpsModuleSourceRootType<*>>): Boolean {
    return rootManager.contentEntries.any { it.getSourceFolders(rootTypes).isNotEmpty() }
}

/**
 * [forcedModuleInfo] provides a [ModuleInfo] instance for a dummy file. It must not be changed after the first assignment because
 * [ModuleInfoProvider] might cache the module info.
 */
@Suppress("UnusedReceiverParameter")
var PsiFile.forcedModuleInfo: ModuleInfo? by UserDataProperty(Key.create("FORCED_MODULE_INFO"))
    @ApiStatus.Internal get
    @ApiStatus.Internal set



fun ModuleInfo.findSdkAcrossDependencies(): SdkInfo? {
    val project = (this as? IdeaModuleInfo)?.project ?: return null
    return SdkInfoCache.getInstance(project).findOrGetCachedSdk(this)
}


fun IdeaModuleInfo.findJvmStdlibAcrossDependencies(): LibraryInfo? {
    val project = project
    return KotlinStdlibCache.getInstance(project).findStdlibInModuleDependencies(this)
}


fun IdeaModuleInfo.supportsFeature(project: Project, feature: LanguageFeature): Boolean {
    return project.service<LanguageSettingsProvider>()
        .getLanguageVersionSettings(this, project)
        .supportsFeature(feature)
}
