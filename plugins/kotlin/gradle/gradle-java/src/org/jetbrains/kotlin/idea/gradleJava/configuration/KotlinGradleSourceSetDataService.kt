// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("DEPRECATION_ERROR", "DEPRECATION", "TYPEALIAS_EXPANSION_DEPRECATION")

package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.config.TargetPlatformKind
import org.jetbrains.kotlin.extensions.ProjectExtensionDescriptor
import org.jetbrains.kotlin.ide.konan.NativeLibraryKind
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.configuration.KOTLIN_GROUP_ID
import org.jetbrains.kotlin.idea.facet.*
import org.jetbrains.kotlin.idea.formatter.ProjectCodeStyleImporter
import org.jetbrains.kotlin.idea.framework.CommonLibraryKind
import org.jetbrains.kotlin.idea.framework.JSLibraryKind
import org.jetbrains.kotlin.idea.framework.detectLibraryKind
import org.jetbrains.kotlin.idea.gradle.configuration.*
import org.jetbrains.kotlin.idea.gradle.configuration.GradlePropertiesFileFacade.Companion.KOTLIN_CODE_STYLE_GRADLE_SETTING
import org.jetbrains.kotlin.idea.gradle.configuration.klib.KotlinNativeLibraryNameUtil.KOTLIN_NATIVE_LIBRARY_PREFIX
import org.jetbrains.kotlin.idea.gradle.statistics.KotlinGradleFUSLogger
import org.jetbrains.kotlin.idea.gradleJava.inspections.getResolvedVersionByModuleData
import org.jetbrains.kotlin.idea.gradleTooling.CompilerArgumentsBySourceSet
import org.jetbrains.kotlin.idea.gradleTooling.arguments.CachedExtractedArgsInfo
import org.jetbrains.kotlin.idea.gradleTooling.arguments.CompilerArgumentsCacheHolder
import org.jetbrains.kotlin.idea.platform.tooling
import org.jetbrains.kotlin.idea.roots.findAll
import org.jetbrains.kotlin.idea.roots.migrateNonJvmSourceFolders
import org.jetbrains.kotlin.library.KLIB_FILE_EXTENSION
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.impl.isCommon
import org.jetbrains.kotlin.platform.impl.isJavaScript
import org.jetbrains.kotlin.platform.impl.isJvm
import org.jetbrains.kotlin.psi.UserDataProperty
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

@Suppress("TYPEALIAS_EXPANSION_DEPRECATION", "DEPRECATION", "UNUSED")
@Deprecated("Compiler arguments are stored in KotlinGradleSourceSetData nodes cached", level = DeprecationLevel.ERROR)
var Module.compilerArgumentsBySourceSet
        by UserDataProperty(Key.create<CompilerArgumentsBySourceSet>("CURRENT_COMPILER_ARGUMENTS"))

var Module.sourceSetName
        by UserDataProperty(Key.create<String>("SOURCE_SET_NAME"))

interface GradleProjectImportHandler {
    companion object : ProjectExtensionDescriptor<GradleProjectImportHandler>(
        "org.jetbrains.kotlin.gradleProjectImportHandler",
        GradleProjectImportHandler::class.java
    )

    fun importBySourceSet(facet: KotlinFacet, sourceSetNode: DataNode<GradleSourceSetData>)
    fun importByModule(facet: KotlinFacet, moduleNode: DataNode<ModuleData>)
}

class KotlinGradleProjectSettingsDataService : AbstractProjectDataService<ProjectData, Void>() {
    override fun getTargetDataKey() = ProjectKeys.PROJECT

    override fun postProcess(
        toImport: MutableCollection<out DataNode<ProjectData>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModifiableModelsProvider
    ) {
        val allSettings = modelsProvider.modules.mapNotNull { module ->
            if (module.isDisposed) return@mapNotNull null
            val settings = modelsProvider
                .getModifiableFacetModel(module)
                .findFacet(KotlinFacetType.TYPE_ID, KotlinFacetType.INSTANCE.defaultFacetName)
                ?.configuration
                ?.settings ?: return@mapNotNull null
            if (settings.useProjectSettings) null else settings
        }
        val languageVersion = allSettings.asSequence().mapNotNullTo(LinkedHashSet()) { it.languageLevel }.singleOrNull()
        val apiVersion = allSettings.asSequence().mapNotNullTo(LinkedHashSet()) { it.apiLevel }.singleOrNull()
        KotlinCommonCompilerArgumentsHolder.getInstance(project).update {
            if (languageVersion != null) {
                this.languageVersion = languageVersion.versionString
            }
            if (apiVersion != null) {
                this.apiVersion = apiVersion.versionString
            }
        }
    }
}

class KotlinGradleSourceSetDataService : AbstractProjectDataService<GradleSourceSetData, Void>() {
    override fun getTargetDataKey() = GradleSourceSetData.KEY

    override fun postProcess(
        toImport: Collection<DataNode<GradleSourceSetData>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModifiableModelsProvider
    ) {
        for (sourceSetNode in toImport) {
            val sourceSetData = sourceSetNode.data
            val ideModule = modelsProvider.findIdeModule(sourceSetData) ?: continue

            val moduleNode = ExternalSystemApiUtil.findParent(sourceSetNode, ProjectKeys.MODULE) ?: continue
            val kotlinFacet = configureFacetByGradleModule(ideModule, modelsProvider, moduleNode, sourceSetNode) ?: continue
            GradleProjectImportHandler.getInstances(project).forEach { it.importBySourceSet(kotlinFacet, sourceSetNode) }
        }
    }
}

class KotlinGradleProjectDataService : AbstractProjectDataService<ModuleData, Void>() {
    override fun getTargetDataKey() = ProjectKeys.MODULE

    override fun postProcess(
        toImport: MutableCollection<out DataNode<ModuleData>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModifiableModelsProvider
    ) {
        for (moduleNode in toImport) {
            // If source sets are present, configure facets in the their modules
            if (ExternalSystemApiUtil.getChildren(moduleNode, GradleSourceSetData.KEY).isNotEmpty()) continue

            val moduleData = moduleNode.data
            val ideModule = modelsProvider.findIdeModule(moduleData) ?: continue
            val kotlinFacet = configureFacetByGradleModule(ideModule, modelsProvider, moduleNode, null) ?: continue
            GradleProjectImportHandler.getInstances(project).forEach { it.importByModule(kotlinFacet, moduleNode) }
        }

        runReadAction {
            val codeStyleStr = GradlePropertiesFileFacade.forProject(project).readProperty(KOTLIN_CODE_STYLE_GRADLE_SETTING)
            ProjectCodeStyleImporter.apply(project, codeStyleStr)
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            KotlinGradleFUSLogger.reportStatistics()
        }
    }
}

class KotlinGradleLibraryDataService : AbstractProjectDataService<LibraryData, Void>() {
    override fun getTargetDataKey() = ProjectKeys.LIBRARY

    override fun postProcess(
        toImport: MutableCollection<out DataNode<LibraryData>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModifiableModelsProvider
    ) {
        if (toImport.isEmpty()) return
        val projectDataNode = toImport.first().parent!!

        @Suppress("UNCHECKED_CAST")
        val moduleDataNodes = projectDataNode.children.filter { it.data is ModuleData } as List<DataNode<ModuleData>>
        val anyNonJvmModules = moduleDataNodes
            .any { node -> detectPlatformKindByPlugin(node)?.takeIf { !it.isJvm } != null }

        for (libraryDataNode in toImport) {
            val ideLibrary = modelsProvider.findIdeLibrary(libraryDataNode.data) ?: continue

            val modifiableModel = modelsProvider.getModifiableLibraryModel(ideLibrary) as LibraryEx.ModifiableModelEx
            if (anyNonJvmModules || ideLibrary.looksAsNonJvmLibrary()) {
                detectLibraryKind(modifiableModel.getFiles(OrderRootType.CLASSES))?.let { modifiableModel.kind = it }
            } else if (
                ideLibrary is LibraryEx &&
                (ideLibrary.kind === JSLibraryKind || ideLibrary.kind === NativeLibraryKind || ideLibrary.kind === CommonLibraryKind)
            ) {
                modifiableModel.forgetKind()
            }
        }
    }

    private fun Library.looksAsNonJvmLibrary(): Boolean {
        name?.let { name ->
            if (nonJvmSuffixes.any { it in name } || name.startsWith(KOTLIN_NATIVE_LIBRARY_PREFIX))
                return true
        }

        return getFiles(OrderRootType.CLASSES).firstOrNull()?.extension == KLIB_FILE_EXTENSION
    }

    companion object {
        val LOG = Logger.getInstance(KotlinGradleLibraryDataService::class.java)

        val nonJvmSuffixes = listOf("-common", "-js", "-native", "-kjsm", "-metadata")
    }
}

fun detectPlatformKindByPlugin(moduleNode: DataNode<ModuleData>): IdePlatformKind<*>? {
    val pluginId = moduleNode.kotlinGradleProjectDataOrNull?.platformPluginId
    return IdePlatformKind.ALL_KINDS.firstOrNull { it.tooling.gradlePluginId == pluginId }
}

@Suppress("DEPRECATION_ERROR")
@Deprecated(
    "Use detectPlatformKindByPlugin() instead",
    replaceWith = ReplaceWith("detectPlatformKindByPlugin(moduleNode)"),
    level = DeprecationLevel.ERROR
)
fun detectPlatformByPlugin(moduleNode: DataNode<ModuleData>): TargetPlatformKind<*>? {
    return when (moduleNode.kotlinGradleProjectDataOrFail.platformPluginId) {
        "kotlin-platform-jvm" -> TargetPlatformKind.Jvm[JvmTarget.DEFAULT]
        "kotlin-platform-js" -> TargetPlatformKind.JavaScript
        "kotlin-platform-common" -> TargetPlatformKind.Common
        else -> null
    }
}

private fun detectPlatformByLibrary(moduleNode: DataNode<ModuleData>): IdePlatformKind<*>? {
    val detectedPlatforms =
        mavenLibraryIdToPlatform.entries
            .filter { moduleNode.getResolvedVersionByModuleData(KOTLIN_GROUP_ID, listOf(it.key)) != null }
            .map { it.value }.distinct()
    return detectedPlatforms.singleOrNull() ?: detectedPlatforms.firstOrNull { !it.isCommon }
}

@Suppress("unused") // Used in the Android plugin
fun configureFacetByGradleModule(
    moduleNode: DataNode<ModuleData>,
    sourceSetName: String?,
    ideModule: Module,
    modelsProvider: IdeModifiableModelsProvider
): KotlinFacet? {
    return configureFacetByGradleModule(ideModule, modelsProvider, moduleNode, null, sourceSetName)
}

fun configureFacetByGradleModule(
    ideModule: Module,
    modelsProvider: IdeModifiableModelsProvider,
    moduleNode: DataNode<ModuleData>,
    sourceSetNode: DataNode<GradleSourceSetData>?,
    sourceSetName: String? = sourceSetNode?.data?.id?.let { it.substring(it.lastIndexOf(':') + 1) }
): KotlinFacet? {
    if (moduleNode.kotlinSourceSetData?.sourceSetInfo != null) return null // Suppress in the presence of new MPP model
    val kotlinGradleProjectDataNode = moduleNode.kotlinGradleProjectDataNodeOrNull ?: return null
    val kotlinGradleProjectData = kotlinGradleProjectDataNode.data
    if (!kotlinGradleProjectData.isResolved) return null

    if (!kotlinGradleProjectData.hasKotlinPlugin) {
        val facetModel = modelsProvider.getModifiableFacetModel(ideModule)
        val facet = facetModel.getFacetByType(KotlinFacetType.TYPE_ID)
        if (facet != null) {
            facetModel.removeFacet(facet)
        }
        return null
    }

    val platformKind = detectPlatformKindByPlugin(moduleNode) ?: detectPlatformByLibrary(moduleNode)
    val kotlinGradleSourceSetDataNode = kotlinGradleProjectDataNode.findAll(KotlinGradleSourceSetData.KEY)
        .firstOrNull { it.data.sourceSetName == sourceSetName }

    val compilerVersion = kotlinGradleSourceSetDataNode?.data?.kotlinPluginVersion ?: return null


    // TODO there should be a way to figure out the correct platform version
    val platform = platformKind?.defaultPlatform

    val kotlinFacet = ideModule.getOrCreateFacet(modelsProvider, false, GradleConstants.SYSTEM_ID.id)
    kotlinFacet.configureFacet(
        compilerVersion = compilerVersion,
        platform = platform,
        modelsProvider = modelsProvider,
        //TODO ychernyshev: compute additionalVisibleModuleNames for all modules at once to avoid square complexity
        additionalVisibleModuleNames = sourceSetName?.let { getAdditionalVisibleModuleNames(moduleNode, it) }.orEmpty()
    )

    if (sourceSetNode == null) {
        ideModule.sourceSetName = sourceSetName
    }
    ideModule.hasExternalSdkConfiguration = sourceSetNode?.data?.sdkName != null

    val cacheHolder = CompilerArgumentsCacheMergeManager.compilerArgumentsCacheHolder
    val kotlinGradleSourceSetData = kotlinGradleSourceSetDataNode?.data

    val cachedArgsInfo = kotlinGradleSourceSetData?.cachedArgsInfo
    if (cachedArgsInfo != null) {
        configureFacetByCachedCompilerArguments(kotlinFacet, cachedArgsInfo, cacheHolder, modelsProvider)
        kotlinGradleSourceSetData.isProcessed = true
    }

    val implementedModulesAware = (kotlinGradleSourceSetData ?: kotlinGradleProjectData) as ImplementedModulesAware

    with(kotlinFacet.configuration.settings) {
        implementedModuleNames = implementedModulesAware.implementedModuleNames
        configureOutputPaths(moduleNode, platformKind)
    }

    kotlinFacet.noVersionAutoAdvance()

    if (platformKind != null && !platformKind.isJvm) {
        migrateNonJvmSourceFolders(modelsProvider.getModifiableRootModel(ideModule))
    }

    return kotlinFacet
}

private fun KotlinFacetSettings.configureOutputPaths(moduleNode: DataNode<ModuleData>, platformKind: IdePlatformKind<*>?) {

    fun DataNode<KotlinGradleSourceSetData>.compilerArgumentsOrNull(cacheHolder: CompilerArgumentsCacheHolder): CommonCompilerArguments? =
        CachedArgumentsRestoring.restoreExtractedArgs(data.cachedArgsInfo, cacheHolder).currentCompilerArguments

    if (!platformKind.isJavaScript) {
        productionOutputPath = null
        testOutputPath = null
        return
    }

    val cacheHolder = CompilerArgumentsCacheMergeManager.compilerArgumentsCacheHolder
    val kotlinGradleProjectDataNode = moduleNode.kotlinGradleProjectDataNodeOrNull ?: return
    val kotlinGradleSourceSetDataNodes = ExternalSystemApiUtil.findAll(kotlinGradleProjectDataNode, KotlinGradleSourceSetData.KEY)
    kotlinGradleSourceSetDataNodes.find { it.data.sourceSetName == "main" }?.let {
        productionOutputPath = (it.compilerArgumentsOrNull(cacheHolder) as? K2JSCompilerArguments)?.outputFile
    }
    kotlinGradleSourceSetDataNodes.find { it.data.sourceSetName == "test" }?.let {
        testOutputPath = (it.compilerArgumentsOrNull(cacheHolder) as? K2JSCompilerArguments)?.outputFile
    }
}

fun configureFacetByCachedCompilerArguments(
    kotlinFacet: KotlinFacet,
    cachedArgsInfo: CachedExtractedArgsInfo,
    cacheHolder: CompilerArgumentsCacheHolder,
    modelsProvider: IdeModifiableModelsProvider?
) {
    with(CachedArgumentsRestoring.restoreExtractedArgs(cachedArgsInfo, cacheHolder)) {
        applyCompilerArgumentsToFacet(currentCompilerArguments, defaultCompilerArguments, kotlinFacet, modelsProvider)
        adjustClasspath(kotlinFacet, dependencyClasspath.toList())
    }
}

private fun getAdditionalVisibleModuleNames(moduleNode: DataNode<ModuleData>, sourceSetName: String): Set<String> {
    val kotlinGradleProjectDataNode = moduleNode.kotlinGradleProjectDataNodeOrNull ?: return emptySet()
    return ExternalSystemApiUtil.findAll(kotlinGradleProjectDataNode, KotlinGradleSourceSetData.KEY)
        .firstOrNull { it.data.sourceSetName == sourceSetName }?.data?.additionalVisibleSourceSets.orEmpty()
        .mapNotNull { additionalVisibleSourceSetName ->
            moduleNode.children.map { it.data }
                .filterIsInstance<GradleSourceSetData>().find { otherGradleSourceSetData ->
                    otherGradleSourceSetData.moduleName == additionalVisibleSourceSetName
                }
        }.map { it.id }
        .toSet()
}

internal fun adjustClasspath(kotlinFacet: KotlinFacet, dependencyClasspath: List<String>) {
    if (dependencyClasspath.isEmpty()) return
    val arguments = kotlinFacet.configuration.settings.compilerArguments as? K2JVMCompilerArguments ?: return
    val fullClasspath = arguments.classpath?.split(File.pathSeparator) ?: emptyList()
    if (fullClasspath.isEmpty()) return
    val newClasspath = fullClasspath - dependencyClasspath
    arguments.classpath = if (newClasspath.isNotEmpty()) newClasspath.joinToString(File.pathSeparator) else null
}
