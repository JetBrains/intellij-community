// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION_ERROR", "DEPRECATION", "TYPEALIAS_EXPANSION_DEPRECATION")

package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
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
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.extensions.ProjectExtensionDescriptor
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.tooling
import org.jetbrains.kotlin.idea.base.externalSystem.KotlinGradleFacade
import org.jetbrains.kotlin.idea.base.externalSystem.findAll
import org.jetbrains.kotlin.idea.base.platforms.*
import org.jetbrains.kotlin.idea.base.projectStructure.ExternalCompilerVersionProvider
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.configuration.KOTLIN_GROUP_ID
import org.jetbrains.kotlin.idea.facet.*
import org.jetbrains.kotlin.idea.formatter.ProjectCodeStyleImporter
import org.jetbrains.kotlin.idea.gradle.configuration.GradlePropertiesFileFacade
import org.jetbrains.kotlin.idea.gradle.configuration.GradlePropertiesFileFacade.Companion.KOTLIN_CODE_STYLE_GRADLE_SETTING
import org.jetbrains.kotlin.idea.gradle.configuration.ImplementedModulesAware
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinGradleSourceSetData
import org.jetbrains.kotlin.idea.gradle.configuration.klib.KotlinNativeLibraryNameUtil.KOTLIN_NATIVE_LIBRARY_PREFIX
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinSourceSetData
import org.jetbrains.kotlin.idea.gradle.statistics.KotlinGradleFUSLogger
import org.jetbrains.kotlin.idea.gradleJava.inspections.getResolvedVersionByModuleData
import org.jetbrains.kotlin.idea.gradleJava.migrateNonJvmSourceFolders
import org.jetbrains.kotlin.library.KLIB_FILE_EXTENSION
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.impl.isCommon
import org.jetbrains.kotlin.platform.impl.isJavaScript
import org.jetbrains.kotlin.platform.impl.isJvm
import org.jetbrains.kotlin.psi.UserDataProperty
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

var Module.sourceSetName
        by UserDataProperty(Key.create<String>("SOURCE_SET_NAME"))

private val logger = Logger.getInstance("#org.jetbrains.kotlin.idea.gradleJava.configuration")


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
        toImport: Collection<DataNode<ProjectData>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModifiableModelsProvider,
    ) {
        runInEdt {
            runWriteAction {
                KotlinCommonCompilerArgumentsHolder.getInstance(project).updateLanguageAndApi(project, modelsProvider.modules)
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
        var maxCompilerVersion: IdeKotlinVersion? = null
        for (sourceSetNode in toImport) {
            val sourceSetData = sourceSetNode.data
            val ideModule = modelsProvider.findIdeModule(sourceSetData) ?: continue

            val moduleNode = ExternalSystemApiUtil.findParent(sourceSetNode, ProjectKeys.MODULE) ?: continue
            val kotlinFacet = configureFacetByGradleModule(ideModule, modelsProvider, moduleNode, sourceSetNode) ?: continue
            val currentModuleCompilerVersion = ExternalCompilerVersionProvider.get(ideModule)
            if (currentModuleCompilerVersion != null) {
                maxCompilerVersion = maxOf(maxCompilerVersion ?: currentModuleCompilerVersion, currentModuleCompilerVersion)
            }
            GradleProjectImportHandler.getInstances(project).forEach { it.importBySourceSet(kotlinFacet, sourceSetNode) }
        }

        if (maxCompilerVersion != null) {
            KotlinJpsPluginSettings.importKotlinJpsVersionFromExternalBuildSystem(
                project,
                maxCompilerVersion.rawVersion,
                isDelegatedToExtBuild = GradleProjectSettings.isDelegatedBuildEnabled(project, projectData?.linkedExternalProjectPath)
            )
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
        if (projectData?.owner != GradleConstants.SYSTEM_ID) return

        for (moduleNode in toImport) {
            // This code is used for when resolveModulePerSourceSet is set to false in the Gradle settings.
            // In this case, a single module is created per Gradle module rather than one per source set,
            // which means the KotlinGradleSourceSetDataService will not be applicable.
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

        project.service<KotlinGradleFUSLogger>().scheduleReportStatistics()
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
                detectLibraryKind(ideLibrary, project)?.let { modifiableModel.kind = it }
            } else if (ideLibrary is LibraryEx && ideLibrary.kind in NON_JVM_LIBRARY_KINDS) {
                modifiableModel.forgetKind()
            }
        }
    }

    private fun Library.looksAsNonJvmLibrary(): Boolean {
        name?.let { name ->
            if (NON_JVM_SUFFIXES.any { it in name } || name.startsWith(KOTLIN_NATIVE_LIBRARY_PREFIX))
                return true
        }

        return getFiles(OrderRootType.CLASSES).firstOrNull()?.extension == KLIB_FILE_EXTENSION
    }

    companion object {
        val LOG = Logger.getInstance(KotlinGradleLibraryDataService::class.java)

        val NON_JVM_LIBRARY_KINDS: List<PersistentLibraryKind<*>> = listOf(
            KotlinJavaScriptLibraryKind,
            KotlinWasmLibraryKind,
            KotlinNativeLibraryKind,
            KotlinCommonLibraryKind
        )

        val NON_JVM_SUFFIXES = listOf("-common", "-js", "-wasm", "-native", "-kjsm", "-metadata")
    }
}

fun detectPlatformKindByPlugin(moduleNode: DataNode<ModuleData>): IdePlatformKind? {
    val pluginId = moduleNode.kotlinGradleProjectDataOrNull?.platformPluginId
    return IdePlatformKind.ALL_KINDS.firstOrNull { it.tooling.gradlePluginId == pluginId }
}

internal fun detectPlatformByLibrary(moduleNode: DataNode<ModuleData>): IdePlatformKind? {
    val detectedPlatforms =
        mavenLibraryIdToPlatform.entries
            .filter { moduleNode.getResolvedVersionByModuleData(KOTLIN_GROUP_ID, listOf(it.key)) != null }
            .map { it.value }.distinct()
    return detectedPlatforms.singleOrNull() ?: detectedPlatforms.firstOrNull { !it.isCommon }
}

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
    val kotlinGradleProjectDataNode = moduleNode.kotlinGradleProjectDataNodeOrNull
    val kotlinGradleProjectData = kotlinGradleProjectDataNode?.data

    if (kotlinGradleProjectData?.isResolved == false) return null
    if (kotlinGradleProjectDataNode == null || kotlinGradleProjectData?.hasKotlinPlugin == false) {
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

    val compilerVersion = kotlinGradleSourceSetDataNode?.data?.kotlinPluginVersion?.let(IdeKotlinVersion::opt)
    // required for GradleFacetImportTest.{testCommonImportByPlatformPlugin, testKotlinAndroidPluginDetection}
        ?: KotlinGradleFacade.getInstance()?.findKotlinPluginVersion(moduleNode)

    if (compilerVersion == null) {
        logger.error("[Kotlin Facet]: cannot create facet for module '${ideModule.name}' due to unknown compiler version. " +
                             "Some functionality might become unavailable!")
        return null
    }

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

    val kotlinGradleSourceSetData = kotlinGradleSourceSetDataNode?.data

    kotlinGradleSourceSetData?.compilerArguments?.let { compilerArguments ->
        configureFacetWithCompilerArguments(kotlinFacet, modelsProvider, compilerArguments)
    }

    val implementedModulesAware = (kotlinGradleSourceSetData ?: kotlinGradleProjectData) as ImplementedModulesAware

    with(kotlinFacet.configuration.settings) {
        implementedModuleNames = implementedModulesAware.implementedModuleNames
        configureOutputPaths(moduleNode, platformKind)
        noVersionAutoAdvance()
    }

    if (platformKind != null && !platformKind.isJvm) {
        migrateNonJvmSourceFolders(
            modelsProvider.getModifiableRootModel(ideModule),
            ExternalSystemApiUtil.toExternalSource(moduleNode.data.owner)
        )
    }

    return kotlinFacet
}

private fun IKotlinFacetSettings.configureOutputPaths(moduleNode: DataNode<ModuleData>, platformKind: IdePlatformKind?) {
    if (!platformKind.isJavaScript) {
        productionOutputPath = null
        testOutputPath = null
        return
    }

    val kotlinGradleProjectDataNode = moduleNode.kotlinGradleProjectDataNodeOrNull ?: return
    val kotlinGradleSourceSetDataNodes = ExternalSystemApiUtil.findAll(kotlinGradleProjectDataNode, KotlinGradleSourceSetData.KEY)
    kotlinGradleSourceSetDataNodes.find { it.data.sourceSetName == "main" }?.let {
        productionOutputPath = (it.data.compilerArguments as? K2JSCompilerArguments)?.outputDir
            ?: (it.data.compilerArguments as? K2JSCompilerArguments)?.outputFile
    }
    kotlinGradleSourceSetDataNodes.find { it.data.sourceSetName == "test" }?.let {
        testOutputPath = (it.data.compilerArguments as? K2JSCompilerArguments)?.outputDir
            ?: (it.data.compilerArguments as? K2JSCompilerArguments)?.outputFile
    }
}

fun configureFacetWithCompilerArguments(
    kotlinFacet: KotlinFacet,
    modelsProvider: IdeModifiableModelsProvider?,
    compilerArguments: CommonCompilerArguments,
) {
    applyCompilerArgumentsToFacetSettings(compilerArguments, kotlinFacet.configuration.settings, kotlinFacet.module, modelsProvider)
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
