// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinProjectStructureUtils")

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.FileIndex
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.analyzer.LanguageSettingsProvider
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.base.facet.isNewMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.kotlinSourceRootType
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.util.Frontend10ApiUsage
import org.jetbrains.kotlin.idea.base.util.runWithAlternativeResolveEnabled
import org.jetbrains.kotlin.psi.UserDataProperty

@Frontend10ApiUsage
val KtModule.moduleInfo: IdeaModuleInfo
    get() {
        require(this is KtModuleByModuleInfoBase)
        return ideaModuleInfo
    }


val KtSourceModule.ideaModule: Module
    get() {
        require(this is KtSourceModuleByModuleInfo)
        return ideaModule
    }

fun Module.getMainKtSourceModule(): KtSourceModule? {
    val moduleInfo = productionSourceInfo ?: return null
    return moduleInfo.toKtModuleOfType<KtSourceModule>()
}

val ModuleInfo.kotlinSourceRootType: KotlinSourceRootType?
    get() = when (this) {
        is ModuleProductionSourceInfo -> SourceKotlinRootType
        is ModuleTestSourceInfo -> TestSourceKotlinRootType
        else -> null
    }

val Module.productionSourceInfo: ModuleProductionSourceInfo?
    get() {
        val hasProductionRoots = hasRootsOfType(setOf(JavaSourceRootType.SOURCE, SourceKotlinRootType))
                || (isNewMultiPlatformModule && kotlinSourceRootType == SourceKotlinRootType)

        return if (hasProductionRoots) ModuleProductionSourceInfo(this) else null
    }

val Module.testSourceInfo: ModuleTestSourceInfo?
    get() {
        val hasTestRoots = hasRootsOfType(setOf(JavaSourceRootType.TEST_SOURCE, TestSourceKotlinRootType))
                || (isNewMultiPlatformModule && kotlinSourceRootType == TestSourceKotlinRootType)

        return if (hasTestRoots) ModuleTestSourceInfo(this) else null
    }

fun Module.asSourceInfo(sourceRootType: KotlinSourceRootType?): ModuleSourceInfoWithExpectedBy? =
    when (sourceRootType) {
        SourceKotlinRootType -> ModuleProductionSourceInfo(this)
        TestSourceKotlinRootType -> ModuleTestSourceInfo(this)
        else -> null
    }

val Module.sourceModuleInfos: List<ModuleSourceInfo>
    get() = listOfNotNull(testSourceInfo, productionSourceInfo)

private fun Module.hasRootsOfType(rootTypes: Set<JpsModuleSourceRootType<*>>): Boolean {
    return rootManager.contentEntries.any { it.getSourceFolders(rootTypes).isNotEmpty() }
}

fun Library.getBinaryAndSourceModuleInfos(project: Project): List<IdeaModuleInfo> = buildList {
    LibraryInfoCache.getInstance(project)[this@getBinaryAndSourceModuleInfos].forEach { libraryInfo ->
        add(libraryInfo)
        add(libraryInfo.sourcesModuleInfo)
    }
}

/**
 * [forcedModuleInfo] provides a [ModuleInfo] instance for a dummy file. It must not be changed after the first assignment because
 * [ModuleInfoProvider] might cache the module info.
 */
@Suppress("UnusedReceiverParameter")
var PsiFile.forcedModuleInfo: ModuleInfo? by UserDataProperty(Key.create("FORCED_MODULE_INFO"))
    @ApiStatus.Internal get
    @ApiStatus.Internal set

private val testRootTypes: Set<JpsModuleSourceRootType<*>> = setOf(
    JavaSourceRootType.TEST_SOURCE,
    JavaResourceRootType.TEST_RESOURCE,
    TestSourceKotlinRootType,
    TestResourceKotlinRootType
)

private val sourceRootTypes = setOf<JpsModuleSourceRootType<*>>(
    JavaSourceRootType.SOURCE,
    JavaResourceRootType.RESOURCE,
    SourceKotlinRootType,
    ResourceKotlinRootType
)

val JpsModuleSourceRootType<*>.sourceRootType: KotlinSourceRootType?
    get() = when (this) {
        in sourceRootTypes -> SourceKotlinRootType
        in testRootTypes -> TestSourceKotlinRootType
        else -> null
    }


fun ProjectFileIndex.getKotlinSourceRootType(virtualFile: VirtualFile): KotlinSourceRootType? {
    // Ignore injected files
    if (virtualFile is VirtualFileWindow) {
        return null
    }

    return runReadAction {
        val sourceRootType = getContainingSourceRootType(virtualFile) ?: return@runReadAction null
        when (sourceRootType) {
          in testRootTypes -> TestSourceKotlinRootType
          in sourceRootTypes -> SourceKotlinRootType
          else -> null
        }
    }
}

@Deprecated("use ProjectFileIndex.getKotlinSourceRootType(VirtualFile)")
fun FileIndex.getKotlinSourceRootType(virtualFile: VirtualFile): KotlinSourceRootType? {
    // Ignore injected files
    if (virtualFile is VirtualFileWindow) {
        return null
    }

    return runReadAction {
        when {
            isUnderSourceRootOfType(virtualFile, testRootTypes) -> TestSourceKotlinRootType
            isInSourceContent(virtualFile) -> SourceKotlinRootType
            else -> null
        }
    }
}

@RequiresReadLock
fun GlobalSearchScope.hasKotlinJvmRuntime(project: Project): Boolean {
    return project.runWithAlternativeResolveEnabled {
        try {
            val markerClassName = StandardNames.FqNames.unit.asString()
            JavaPsiFacade.getInstance(project).findClass(markerClassName, this@hasKotlinJvmRuntime) != null
        } catch (e: IndexNotReadyException) {
            false
        }
    }
}

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

@ApiStatus.Internal
fun JpsModuleSourceRoot.getMigratedSourceRootTypeWithProperties(): Pair<JpsModuleSourceRootType<JpsElement>, JpsElement>? {
    val currentRootType = rootType

    @Suppress("UNCHECKED_CAST")
    val newSourceRootType: JpsModuleSourceRootType<JpsElement> = when (currentRootType) {
        JavaSourceRootType.SOURCE -> SourceKotlinRootType as JpsModuleSourceRootType<JpsElement>
        JavaSourceRootType.TEST_SOURCE -> TestSourceKotlinRootType
        JavaResourceRootType.RESOURCE -> ResourceKotlinRootType
        JavaResourceRootType.TEST_RESOURCE -> TestResourceKotlinRootType
        else -> return null
    } as JpsModuleSourceRootType<JpsElement>

    val properties = getProperties(rootType)?.also { (it as? JpsElementBase<*>)?.setParent(null) }
        ?: rootType.createDefaultProperties()

    return newSourceRootType to properties
}