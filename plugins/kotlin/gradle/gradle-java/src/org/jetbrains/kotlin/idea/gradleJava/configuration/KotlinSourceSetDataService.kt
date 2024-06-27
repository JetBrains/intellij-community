// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.service.project.manage.SourceFolderManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExportableOrderEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.tooling
import org.jetbrains.kotlin.idea.base.externalSystem.KotlinGradleFacade
import org.jetbrains.kotlin.idea.facet.*
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinSourceSetInfo
import org.jetbrains.kotlin.idea.gradle.configuration.findChildModuleById
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinAndroidSourceSets
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinSourceSetData
import org.jetbrains.kotlin.idea.gradleJava.migrateNonJvmSourceFolders
import org.jetbrains.kotlin.idea.gradleJava.pathAsUrl
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinComponent
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet.Companion.COMMON_MAIN_SOURCE_SET_NAME
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet.Companion.COMMON_TEST_SOURCE_SET_NAME
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.JsPlatform
import org.jetbrains.kotlin.platform.SimplePlatform
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.kotlin.platform.impl.NativeIdePlatformKind
import org.jetbrains.kotlin.platform.impl.WasmIdePlatformKind
import org.jetbrains.kotlin.platform.jvm.JvmPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.NativePlatform
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.platform.wasm.WasmPlatform
import org.jetbrains.kotlin.platform.wasm.WasmPlatforms
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants

class KotlinSourceSetDataService : AbstractProjectDataService<GradleSourceSetData, Void>() {
    override fun getTargetDataKey() = GradleSourceSetData.KEY

    private fun getProjectPlatforms(toImport: MutableCollection<out DataNode<GradleSourceSetData>>): List<KotlinPlatform> {
        val platforms = HashSet<KotlinPlatform>()

        for (nodeToImport in toImport) {
            nodeToImport.kotlinSourceSetData?.sourceSetInfo?.also {
                platforms += it.actualPlatforms.platforms
            }

            if (nodeToImport.parent?.children?.any { it.key.dataType.contains("Android") } == true) {
                platforms += KotlinPlatform.ANDROID
            }
        }

        return platforms.toList()
    }

    override fun postProcess(
        toImport: MutableCollection<out DataNode<GradleSourceSetData>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModifiableModelsProvider
    ) {
        val projectPlatforms = getProjectPlatforms(toImport)

        for (nodeToImport in toImport) {
            val mainModuleData = ExternalSystemApiUtil.findParent(
                nodeToImport,
                ProjectKeys.MODULE
            ) ?: continue
            val sourceSetData = nodeToImport.data
            val kotlinSourceSet = nodeToImport.kotlinSourceSetData?.sourceSetInfo ?: continue
            val ideModule = modelsProvider.findIdeModule(sourceSetData) ?: continue
            val platform = kotlinSourceSet.actualPlatforms
            val rootModel = modelsProvider.getModifiableRootModel(ideModule)

            if (platform.platforms.any { (it != KotlinPlatform.JVM && it != KotlinPlatform.ANDROID)} ||
                listOf(COMMON_MAIN_SOURCE_SET_NAME, COMMON_TEST_SOURCE_SET_NAME).contains(sourceSetData.moduleName)) {
                migrateNonJvmSourceFolders(rootModel, ExternalSystemApiUtil.toExternalSource(nodeToImport.data.owner))
                populateNonJvmSourceRootTypes(nodeToImport, ideModule)
            }

            configureFacet(sourceSetData, kotlinSourceSet, mainModuleData, ideModule, modelsProvider, projectPlatforms).let { facet ->
                GradleProjectImportHandler.getInstances(project).forEach { it.importBySourceSet(facet, nodeToImport) }
            }

            if (kotlinSourceSet.isTestModule) {
                assignTestScope(rootModel)
            }
        }
    }

    private fun assignTestScope(rootModel: ModifiableRootModel) {
        rootModel
            .orderEntries
            .asSequence()
            .filterIsInstance<ExportableOrderEntry>()
            .filter { it.scope == DependencyScope.COMPILE }
            .forEach { it.scope = DependencyScope.TEST }
    }

    companion object {
        private val KotlinComponent.kind
            get() = when (this) {
                is KotlinCompilation -> KotlinModuleKind.COMPILATION_AND_SOURCE_SET_HOLDER
                is KotlinSourceSet -> KotlinModuleKind.SOURCE_SET_HOLDER
                else -> KotlinModuleKind.DEFAULT
            }

        private fun SimplePlatform.isRelevantFor(projectPlatforms: List<KotlinPlatform>): Boolean {
            val jvmPlatforms = listOf(KotlinPlatform.ANDROID, KotlinPlatform.JVM, KotlinPlatform.COMMON)
            return when (this) {
                is JvmPlatform -> projectPlatforms.intersect(jvmPlatforms).isNotEmpty()
                is JsPlatform -> KotlinPlatform.JS in projectPlatforms
                is WasmPlatform -> KotlinPlatform.WASM in projectPlatforms
                is NativePlatform -> KotlinPlatform.NATIVE in projectPlatforms
                else -> true
            }
        }

        private fun IdePlatformKind.toSimplePlatforms(
            mainModuleNode: DataNode<ModuleData>,
            sourceSetModuleData: ModuleData,
            sourceSetInfo: KotlinSourceSetInfo,
            isHmppModule: Boolean,
            projectPlatforms: List<KotlinPlatform>
        ): Collection<SimplePlatform> {
            if (this is JvmIdePlatformKind) {
                val jvmTarget = inferJvmTarget(mainModuleNode, sourceSetInfo)
                return JvmPlatforms.jvmPlatformByTargetVersion(jvmTarget)
            }

            if (this is NativeIdePlatformKind) {
                return NativePlatforms.nativePlatformByTargetNames(sourceSetModuleData.konanTargets)
            }

            if (this is WasmIdePlatformKind) {
                return WasmPlatforms.wasmPlatformByTargetNames(sourceSetModuleData.wasmTargets)
            }

            return if (isHmppModule) {
                this.defaultPlatform.filter { it.isRelevantFor(projectPlatforms) }
            } else {
                this.defaultPlatform
            }
        }

        /**
         * Will infer the [JvmTarget] of the provided [sourceSet] by looking into imported compiler arguments.
         * This method will take dependsOn edges into account.
         *
         * In case of conflicting jvmTargets (e.g. 1.8 vs 11) the smallest one will be returned as convention
         */
        private fun inferJvmTarget(mainModuleNode: DataNode<out ModuleData>, sourceSet: KotlinSourceSetInfo): JvmTarget {
            /**
             * All SourceSets that dependOn the given [sourceSet] (plus the [sourceSet] itself).
             * ### Example: Simple multiplatform setup with jvm and multiple iOS targets:
             * In this case this will return `[commonMain, jvmMain]` for when the [sourceSet] is 'commonMain'
             * and `[jvmMain]`  if the [sourceSet] is jvmMain
             */
            val sourceSetWithDependingSourceSets = ExternalSystemApiUtil.findAll(mainModuleNode, GradleSourceSetData.KEY)
                .mapNotNull { sourceSetNode -> sourceSetNode.kotlinSourceSetData?.sourceSetInfo }
                .filter { otherSourceSetInfo -> sourceSet.moduleId in otherSourceSetInfo.dependsOn }
                .plus(sourceSet)

            val allJvmTargets = sourceSetWithDependingSourceSets
                /* Resolve the compilerArguments, as they will contain the 'jvmTarget' */
                .asSequence()
                .mapNotNull { currentSourceSet -> currentSourceSet.compilerArguments?.get() }

                /* Only K2JVMCompilerArguments will ship the 'jvmTarget' */
                .mapNotNull { compilerArguments -> compilerArguments as? K2JVMCompilerArguments }
                .mapNotNull { jvmCompilerArguments -> jvmCompilerArguments.jvmTarget }
                .mapNotNull { jvmTargetString -> JvmTarget.fromString(jvmTargetString) }
                .toSet()

            /*
            In case of conflict: Choose the **lowest** jvmTarget version.
            Checkers like 'InlinePlatformCompatibilityChecker' will be more useful like this:
            Basically alerting that the code shared across multiple jvm targets will not work for at least one platform.
            */
            return allJvmTargets.minOrNull() ?: JvmTarget.DEFAULT
        }

        fun configureFacet(
            moduleData: ModuleData,
            kotlinSourceSet: KotlinSourceSetInfo,
            mainModuleNode: DataNode<ModuleData>,
            ideModule: Module,
            modelsProvider: IdeModifiableModelsProvider
        ) {
            // TODO Review this code after AS Chipmunk released and merged to master
            // In https://android.googlesource.com/platform/tools/adt/idea/+/ab31cd294775b7914ddefbe417a828b5c18acc81%5E%21/#F1
            // creation of KotlinAndroidSourceSetData node was dropped, all tasks must be stored in corresponding KotlinSourceSetData nodes
            val additionalRunTasks = mainModuleNode.kotlinAndroidSourceSets
                ?.filter { it.isTestModule }
                ?.flatMap { it.externalSystemRunTasks }
                ?.toSet()
            configureFacet(
                moduleData,
                kotlinSourceSet,
                mainModuleNode,
                ideModule,
                modelsProvider,
                enumValues<KotlinPlatform>().toList(),
                additionalRunTasks
            )
        }

        fun configureFacet(
            moduleData: ModuleData,
            kotlinSourceSet: KotlinSourceSetInfo,
            mainModuleNode: DataNode<ModuleData>,
            ideModule: Module,
            modelsProvider: IdeModifiableModelsProvider,
            projectPlatforms: List<KotlinPlatform>,
            additionalRunTasks: Collection<ExternalSystemRunTask>? = null
        ): KotlinFacet {
            val compilerVersion = KotlinGradleFacade.getInstance()?.findKotlinPluginVersion(mainModuleNode)
            // ?: return null TODO: Fix in CLion or our plugin KT-27623

            val platformKinds = kotlinSourceSet.actualPlatforms.platforms //TODO(auskov): fix calculation of jvm target
                .map { kotlinPlatform -> kotlinPlatform.tooling.kind }
                .flatMap { idePlatformKind ->
                    idePlatformKind.toSimplePlatforms(
                        mainModuleNode = mainModuleNode,
                        sourceSetModuleData = moduleData,
                        sourceSetInfo = kotlinSourceSet,
                        isHmppModule = mainModuleNode.kotlinGradleProjectDataOrFail.isHmpp,
                        projectPlatforms = projectPlatforms
                    )
                }
                .distinct()
                .toSet()

            val platform = TargetPlatform(platformKinds)

            val compilerArguments = kotlinSourceSet.compilerArguments?.get()
            // Used ID is the same as used in org/jetbrains/kotlin/idea/configuration/KotlinGradleSourceSetDataService.kt:280
            // because this DataService was separated from KotlinGradleSourceSetDataService for MPP projects only
            val id = if (compilerArguments?.multiPlatform == true) GradleConstants.SYSTEM_ID.id else null
            val kotlinFacet = ideModule.getOrCreateFacet(modelsProvider, false, id)
            val kotlinGradleProjectData = mainModuleNode.kotlinGradleProjectDataOrFail
            kotlinFacet.configureFacet(
                compilerVersion = compilerVersion,
                platform = platform,
                modelsProvider = modelsProvider,
                hmppEnabled = kotlinGradleProjectData.isHmpp,
                pureKotlinSourceFolders = if (platform.isJvm()) kotlinGradleProjectData.pureKotlinSourceFolders.toList() else emptyList(),
                dependsOnList = kotlinSourceSet.dependsOn.toList(),
                additionalVisibleModuleNames = kotlinSourceSet.additionalVisible
            )

            if (compilerArguments != null) {
                applyCompilerArgumentsToFacetSettings(
                    compilerArguments,
                    kotlinFacet.configuration.settings,
                    kotlinFacet.module,
                    modelsProvider
                )
            }

            with(kotlinFacet.configuration.settings) {
                noVersionAutoAdvance()
                kind = kotlinSourceSet.kotlinComponent.kind

                isTestModule = kotlinSourceSet.isTestModule

                externalSystemRunTasks = buildList {
                    addAll(kotlinSourceSet.externalSystemRunTasks)
                    additionalRunTasks?.let(::addAll)
                }

                externalProjectId = kotlinSourceSet.gradleModuleId

                sourceSetNames = kotlinSourceSet.sourceSetIdsByName.values.mapNotNull { sourceSetId ->
                    val node = mainModuleNode.findChildModuleById(sourceSetId) ?: return@mapNotNull null
                    val data = node.data as? ModuleData ?: return@mapNotNull null
                    modelsProvider.findIdeModule(data)?.name
                }

                testOutputPath = (compilerArguments as? K2JSCompilerArguments)?.outputDir
                    ?: (compilerArguments as? K2JSCompilerArguments)?.outputFile
                productionOutputPath = (compilerArguments as? K2JSCompilerArguments)?.outputDir
                    ?: (compilerArguments as? K2JSCompilerArguments)?.outputFile
            }

            return kotlinFacet
        }
    }
}

private const val KOTLIN_NATIVE_TARGETS_PROPERTY = "konanTargets"
private const val KOTLIN_WASM_TARGETS_PROPERTY = "wasmTargets"

var ModuleData.konanTargets: Set<String>
    get() {
        val value = getProperty(KOTLIN_NATIVE_TARGETS_PROPERTY) ?: return emptySet()
        return if (value.isNotEmpty()) value.split(',').toSet() else emptySet()
    }
    set(value) = setProperty(KOTLIN_NATIVE_TARGETS_PROPERTY, value.takeIf { it.isNotEmpty() }?.joinToString(","))

var ModuleData.wasmTargets: Set<String>
    get() {
        val value = getProperty(KOTLIN_WASM_TARGETS_PROPERTY) ?: return emptySet()
        return if (value.isNotEmpty()) value.split(',').toSet() else emptySet()
    }
    set(value) = setProperty(KOTLIN_WASM_TARGETS_PROPERTY, value.takeIf { it.isNotEmpty() }?.joinToString(","))

private fun populateNonJvmSourceRootTypes(sourceSetNode: DataNode<GradleSourceSetData>, module: Module) {
    val sourceFolderManager = SourceFolderManager.getInstance(module.project)
    val contentRootDataNodes = ExternalSystemApiUtil.findAll(sourceSetNode, ProjectKeys.CONTENT_ROOT)
    val contentRootDataList = contentRootDataNodes.mapNotNull { it.data }
    if (contentRootDataList.isEmpty()) return

    val externalToKotlinSourceTypes = mapOf(
        ExternalSystemSourceType.SOURCE to SourceKotlinRootType,
        ExternalSystemSourceType.TEST to TestSourceKotlinRootType
    )
    externalToKotlinSourceTypes.forEach { (externalType, kotlinType) ->
        val sourcesRoots = contentRootDataList.flatMap { it.getPaths(externalType) }
        sourcesRoots.forEach {
            if (!FileUtil.exists(it.path)) {
                sourceFolderManager.addSourceFolder(module, it.pathAsUrl, kotlinType)
            }
        }
    }
}
