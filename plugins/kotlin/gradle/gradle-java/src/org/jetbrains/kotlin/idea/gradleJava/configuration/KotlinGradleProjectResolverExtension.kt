// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import org.gradle.api.artifacts.Dependency
import org.gradle.internal.impldep.org.apache.commons.lang.math.RandomUtils
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinGradleProjectData
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinGradleSourceSetData
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinGradleSourceSetDataNodes
import org.jetbrains.kotlin.idea.gradle.statistics.KotlinGradleFUSLogger
import org.jetbrains.kotlin.idea.gradleJava.inspections.getDependencyModules
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.kotlin.idea.projectModel.KotlinTarget
import org.jetbrains.kotlin.idea.statistics.KotlinIDEGradleActionsFUSCollector
import org.jetbrains.kotlin.idea.util.NotNullableCopyableDataNodeUserDataProperty
import org.jetbrains.kotlin.idea.util.PsiPrecedences
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.jetbrains.plugins.gradle.model.ExternalProjectDependency
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import org.jetbrains.plugins.gradle.model.FileCollectionDependency
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.io.File
import java.util.*

val DataNode<out ModuleData>.kotlinGradleProjectDataNodeOrNull: DataNode<KotlinGradleProjectData>?
    get() = when (this.data) {
        is GradleSourceSetData -> ExternalSystemApiUtil.findParent(this, ProjectKeys.MODULE)?.kotlinGradleProjectDataNodeOrNull
        else -> ExternalSystemApiUtil.find(this, KotlinGradleProjectData.KEY)
    }

val DataNode<out ModuleData>.kotlinGradleProjectDataNodeOrFail: DataNode<KotlinGradleProjectData>
    get() = kotlinGradleProjectDataNodeOrNull
        ?: error("Failed to find KotlinGradleProjectData node for $this")

val DataNode<out ModuleData>.kotlinGradleProjectDataOrNull: KotlinGradleProjectData?
    get() = when (this.data) {
        is GradleSourceSetData -> ExternalSystemApiUtil.findParent(this, ProjectKeys.MODULE)?.kotlinGradleProjectDataOrNull
        else -> kotlinGradleProjectDataNodeOrNull?.data
    }

val DataNode<out ModuleData>.kotlinGradleProjectDataOrFail: KotlinGradleProjectData
    get() = kotlinGradleProjectDataOrNull
        ?: error("Failed to find KotlinGradleProjectData for $this")

@Deprecated("Use KotlinGradleSourceSetData#isResolved instead", level = DeprecationLevel.ERROR)
var DataNode<out ModuleData>.isResolved: Boolean
    get() = kotlinGradleProjectDataOrFail.isResolved
    set(value) {
        kotlinGradleProjectDataOrFail.isResolved = value
    }

@Deprecated("Use KotlinGradleSourceSetData#hasKotlinPlugin instead", level = DeprecationLevel.ERROR)
var DataNode<out ModuleData>.hasKotlinPlugin: Boolean
    get() = kotlinGradleProjectDataOrFail.hasKotlinPlugin
    set(value) {
        kotlinGradleProjectDataOrFail.hasKotlinPlugin = value
    }

@Suppress("TYPEALIAS_EXPANSION_DEPRECATION")
@Deprecated("Use KotlinGradleSourceSetData#compilerArgumentsBySourceSet instead", level = DeprecationLevel.ERROR)
var DataNode<out ModuleData>.compilerArgumentsBySourceSet: CompilerArgumentsBySourceSet?
    @Suppress("DEPRECATION_ERROR")
    get() = compilerArgumentsBySourceSet()
    set(value) = throw UnsupportedOperationException("Changing of compilerArguments is available only through GradleSourceSetData.")

@Suppress("TYPEALIAS_EXPANSION_DEPRECATION", "DEPRECATION_ERROR")
fun DataNode<out ModuleData>.compilerArgumentsBySourceSet(): CompilerArgumentsBySourceSet? =
    ExternalSystemApiUtil.findAllRecursively(this, KotlinGradleSourceSetData.KEY).ifNotEmpty {
        map { it.data }.filter { it.sourceSetName != null }.associate { it.sourceSetName!! to it.compilerArguments }
    }

@Deprecated("Use KotlinGradleSourceSetData#additionalVisibleSourceSets instead", level = DeprecationLevel.ERROR)
var DataNode<out ModuleData>.additionalVisibleSourceSets: AdditionalVisibleSourceSetsBySourceSet
    @Suppress("DEPRECATION_ERROR")
    get() = ExternalSystemApiUtil.findAllRecursively(this, KotlinGradleSourceSetData.KEY)
        .map { it.data }
        .filter { it.sourceSetName != null }
        .associate { it.sourceSetName!! to it.additionalVisibleSourceSets }
    set(value) {
        ExternalSystemApiUtil.findAllRecursively(this, KotlinGradleSourceSetData.KEY).filter { it.data.sourceSetName != null }.forEach {
            if (value.containsKey(it.data.sourceSetName!!))
                it.data.additionalVisibleSourceSets = value.getValue(it.data.sourceSetName!!)
        }
    }

@Deprecated("Use KotlinGradleSourceSetData#coroutines instead", level = DeprecationLevel.ERROR)
var DataNode<out ModuleData>.coroutines: String?
    get() = kotlinGradleProjectDataOrFail.coroutines
    set(value) {
        kotlinGradleProjectDataOrFail.coroutines = value
    }

@Deprecated("Use KotlinGradleSourceSetData#isHmpp instead", level = DeprecationLevel.ERROR)
var DataNode<out ModuleData>.isHmpp: Boolean
    get() = kotlinGradleProjectDataOrFail.isHmpp
    set(value) {
        kotlinGradleProjectDataOrFail.isHmpp = value
    }

@Deprecated("Use KotlinGradleSourceSetData#platformPluginId instead", level = DeprecationLevel.ERROR)
var DataNode<out ModuleData>.platformPluginId: String?
    get() = kotlinGradleProjectDataOrFail.platformPluginId
    set(value) {
        kotlinGradleProjectDataOrFail.platformPluginId = value
    }

@Deprecated("Use KotlinGradleSourceSetData#kotlinNativeHome instead", level = DeprecationLevel.ERROR)
var DataNode<out ModuleData>.kotlinNativeHome: String
    get() = kotlinGradleProjectDataOrFail.kotlinNativeHome
    set(value) {
        kotlinGradleProjectDataOrFail.kotlinNativeHome = value
    }

@Deprecated("Use KotlinGradleSourceSetData#implementedModuleNames instead", level = DeprecationLevel.ERROR)
var DataNode<out ModuleData>.implementedModuleNames: List<String>
    @Suppress("DEPRECATION_ERROR")
    get() = when (data) {
        is GradleSourceSetData -> ExternalSystemApiUtil.find(this, KotlinGradleSourceSetData.KEY)?.data?.implementedModuleNames
            ?: error("Failed to find KotlinGradleSourceSetData for $this")
        else -> ExternalSystemApiUtil.find(this@implementedModuleNames, KotlinGradleProjectData.KEY)?.data?.implementedModuleNames
            ?: error("Failed to find KotlinGradleProjectData for $this")
    }
    set(value) = throw UnsupportedOperationException("Changing of implementedModuleNames is available only through KotlinGradleSourceSetData.")


@Deprecated("Use KotlinGradleSourceSetData#dependenciesCache instead", level = DeprecationLevel.ERROR)
// Project is usually the same during all import, thus keeping Map Project->Dependencies makes model a bit more complicated but allows to avoid future problems
var DataNode<out ModuleData>.dependenciesCache: MutableMap<DataNode<ProjectData>, Collection<DataNode<out ModuleData>>>
    get() = kotlinGradleProjectDataOrFail.dependenciesCache
    set(value) = with(kotlinGradleProjectDataOrFail.dependenciesCache) {
        clear()
        putAll(value)
    }


@Deprecated("Use KotlinGradleSourceSetData#implementedModuleNames instead", level = DeprecationLevel.ERROR)
var DataNode<out ModuleData>.pureKotlinSourceFolders: MutableCollection<String>
    get() = kotlinGradleProjectDataOrFail.pureKotlinSourceFolders
    set(value) = with(kotlinGradleProjectDataOrFail.pureKotlinSourceFolders) {
        clear()
        addAll(value)
    }


class KotlinGradleProjectResolverExtension : AbstractProjectResolverExtension() {
    val isAndroidProjectKey = Key.findKeyByName("IS_ANDROID_PROJECT_KEY")
    private val cacheManager = KotlinCompilerArgumentsCacheMergeManager


    override fun getToolingExtensionsClasses(): Set<Class<out Any>> {
        return setOf(KotlinGradleModelBuilder::class.java, KotlinTarget::class.java, RandomUtils::class.java, Unit::class.java)
    }

    override fun getExtraProjectModelClasses(): Set<Class<out Any>> {
        error("getModelProvider() is overridden instead")
    }

    override fun getModelProvider(): ProjectImportModelProvider {
        val isAndroidPluginRequestingKotlinGradleModelKey = Key.findKeyByName("IS_ANDROID_PLUGIN_REQUESTING_KOTLIN_GRADLE_MODEL_KEY")
        val isAndroidPluginRequestingKotlinGradleModel =
            isAndroidPluginRequestingKotlinGradleModelKey != null && resolverCtx.getUserData(isAndroidPluginRequestingKotlinGradleModelKey) != null
        return AndroidAwareGradleModelProvider(KotlinGradleModel::class.java, isAndroidPluginRequestingKotlinGradleModel)
    }

    override fun createModule(gradleModule: IdeaModule, projectDataNode: DataNode<ProjectData>): DataNode<ModuleData>? {
        return super.createModule(gradleModule, projectDataNode)?.also {
            cacheManager.mergeCache(gradleModule, resolverCtx)
            initializeModuleData(gradleModule, it, projectDataNode, resolverCtx)
        }
    }

    private fun initializeModuleData(
        gradleModule: IdeaModule,
        mainModuleNode: DataNode<ModuleData>,
        projectDataNode: DataNode<ProjectData>,
        resolverCtx: ProjectResolverContext
    ) {
        LOG.logDebugIfEnabled("Start initialize data for Gradle module: [$gradleModule], Ide module: [$mainModuleNode], Ide project: [$projectDataNode]")

        val mppModel = resolverCtx.getMppModel(gradleModule)
        val project = resolverCtx.externalSystemTaskId.findProject()
        if (mppModel != null) {
            mppModel.targets.forEach { target ->
                KotlinIDEGradleActionsFUSCollector.logImport(
                    project,
                    "MPP.${target.platform.id + (target.presetName?.let { ".$it" } ?: "")}")
            }
            return
        }

        val gradleModel = resolverCtx.getExtraProject(gradleModule, KotlinGradleModel::class.java) ?: return

        if (gradleModel.hasKotlinPlugin) {
            KotlinIDEGradleActionsFUSCollector.logImport(project, gradleModel.kotlinTarget ?: "unknown")
        }

        KotlinGradleProjectData().apply {
            isResolved = true
            kotlinTarget = gradleModel.kotlinTarget
            hasKotlinPlugin = gradleModel.hasKotlinPlugin
            coroutines = gradleModel.coroutines
            platformPluginId = gradleModel.platformPluginId
            pureKotlinSourceFolders.addAll(
                gradleModel.kotlinTaskProperties.flatMap { it.value.pureKotlinSourceFolders ?: emptyList() }.map { it.absolutePath }
            )
            mainModuleNode.createChild(KotlinGradleProjectData.KEY, this)
        }
        if (gradleModel.hasKotlinPlugin) {
            initializeGradleSourceSetsData(gradleModel, mainModuleNode)
        }

    }

    private fun initializeGradleSourceSetsData(kotlinModel: KotlinGradleModel, mainModuleNode: DataNode<ModuleData>) {
        kotlinModel.cachedCompilerArgumentsBySourceSet.forEach { (sourceSetName, cachedArgs) ->
            KotlinGradleSourceSetData(sourceSetName).apply {
                cachedArgsInfo = cachedArgs
                additionalVisibleSourceSets = kotlinModel.additionalVisibleSourceSets.getValue(sourceSetName)
                kotlinPluginVersion = kotlinModel.kotlinTaskProperties.getValue(sourceSetName).pluginVersion
                mainModuleNode.kotlinGradleProjectDataNodeOrFail.createChild(KotlinGradleSourceSetData.KEY, this)
            }
        }
    }

    private fun useModulePerSourceSet(): Boolean {
        // See AndroidGradleProjectResolver
        if (isAndroidProjectKey != null && resolverCtx.getUserData(isAndroidProjectKey) == true) {
            return false
        }
        return resolverCtx.isResolveModulePerSourceSet
    }

    private fun getDependencyByFiles(
        files: Collection<File>,
        outputToSourceSet: Map<String, com.intellij.openapi.util.Pair<String, ExternalSystemSourceType>>?,
        sourceSetByName: Map<String, com.intellij.openapi.util.Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>>?
    ) = files
        .mapTo(HashSet()) {
            val path = FileUtil.toSystemIndependentName(it.path)
            val targetSourceSetId = outputToSourceSet?.get(path)?.first ?: return@mapTo null
            sourceSetByName?.get(targetSourceSetId)?.first
        }
        .singleOrNull()

    private fun DataNode<out ModuleData>.getDependencies(ideProject: DataNode<ProjectData>): Collection<DataNode<out ModuleData>> {
        val cache = kotlinGradleProjectDataOrNull?.dependenciesCache ?: dependencyCacheFallback
        if (cache.containsKey(ideProject)) {
            return cache.getValue(ideProject)
        }
        val outputToSourceSet = ideProject.getUserData(GradleProjectResolver.MODULES_OUTPUTS)
        val sourceSetByName = ideProject.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS) ?: return emptySet()

        val externalSourceSet = sourceSetByName[data.id]?.second ?: return emptySet()
        val result = externalSourceSet.dependencies.mapNotNullTo(LinkedHashSet()) { dependency ->
            when (dependency) {
                is ExternalProjectDependency -> {
                    if (dependency.configurationName == Dependency.DEFAULT_CONFIGURATION) {
                        @Suppress("UNCHECKED_CAST") val targetModuleNode = ExternalSystemApiUtil.findFirstRecursively(ideProject) {
                            (it.data as? ModuleData)?.id == dependency.projectPath
                        } as DataNode<ModuleData>? ?: return@mapNotNullTo null
                        ExternalSystemApiUtil.findAll(targetModuleNode, GradleSourceSetData.KEY)
                            .firstOrNull { it.sourceSetName == "main" }
                    } else {
                        getDependencyByFiles(dependency.projectDependencyArtifacts, outputToSourceSet, sourceSetByName)
                    }
                }
                is FileCollectionDependency -> {
                    getDependencyByFiles(dependency.files, outputToSourceSet, sourceSetByName)
                }
                else -> null
            }
        }
        cache[ideProject] = result
        return result
    }

    private fun addTransitiveDependenciesOnImplementedModules(
        gradleModule: IdeaModule,
        ideModule: DataNode<ModuleData>,
        ideProject: DataNode<ProjectData>
    ) {
        val moduleNodesToProcess = if (useModulePerSourceSet()) {
            ExternalSystemApiUtil.findAll(ideModule, GradleSourceSetData.KEY)
        } else listOf(ideModule)

        val ideaModulesByGradlePaths = gradleModule.project.modules.groupBy { it.gradleProject.path }
        var dirtyDependencies = true
        for (currentModuleNode in moduleNodesToProcess) {
            val toProcess = ArrayDeque<DataNode<out ModuleData>>().apply { add(currentModuleNode) }
            val discovered = HashSet<DataNode<out ModuleData>>().apply { add(currentModuleNode) }

            while (toProcess.isNotEmpty()) {
                val moduleNode = toProcess.pollLast()

                val moduleNodeForGradleModel = if (useModulePerSourceSet()) {
                    ExternalSystemApiUtil.findParent(moduleNode, ProjectKeys.MODULE)
                } else moduleNode

                val ideaModule = if (moduleNodeForGradleModel != ideModule) {
                    moduleNodeForGradleModel?.data?.id?.let { ideaModulesByGradlePaths[it]?.firstOrNull() }
                } else gradleModule

                val implementsModuleIds = resolverCtx.getExtraProject(ideaModule, KotlinGradleModel::class.java)?.implements
                    ?: emptyList()

                for (implementsModuleId in implementsModuleIds) {
                    val targetModule = findModuleById(ideProject, gradleModule, implementsModuleId) ?: continue

                    if (useModulePerSourceSet()) {
                        val targetSourceSetsByName = ExternalSystemApiUtil
                            .findAll(targetModule, GradleSourceSetData.KEY)
                            .associateBy { it.sourceSetName }
                        val targetMainSourceSet = targetSourceSetsByName["main"] ?: targetModule
                        val targetSourceSet = targetSourceSetsByName[currentModuleNode.sourceSetName]
                        if (targetSourceSet != null) {
                            addDependency(currentModuleNode, targetSourceSet)
                        }
                        if (currentModuleNode.sourceSetName == "test" && targetMainSourceSet != targetSourceSet) {
                            addDependency(currentModuleNode, targetMainSourceSet)
                        }
                    } else {
                        dirtyDependencies = true
                        addDependency(currentModuleNode, targetModule)
                    }
                }

                val dependencies = if (useModulePerSourceSet()) {
                    moduleNode.getDependencies(ideProject)
                } else {
                    if (dirtyDependencies) getDependencyModules(ideModule, gradleModule.project).also {
                        dirtyDependencies = false
                    } else emptyList()
                }
                // queue only those dependencies that haven't been discovered earlier
                dependencies.filterTo(toProcess, discovered::add)
            }
        }
    }

    override fun populateModuleDependencies(
        gradleModule: IdeaModule,
        ideModule: DataNode<ModuleData>,
        ideProject: DataNode<ProjectData>
    ) {
        LOG.logDebugIfEnabled("Start populate module dependencies. Gradle module: [$gradleModule], Ide module: [$ideModule], Ide project: [$ideProject]")
        val mppModel = resolverCtx.getMppModel(gradleModule)
        if (mppModel != null) {
            return super.populateModuleDependencies(gradleModule, ideModule, ideProject)
        }


        val gradleModel = resolverCtx.getExtraProject(gradleModule, KotlinGradleModel::class.java)
            ?: return super.populateModuleDependencies(gradleModule, ideModule, ideProject)

        if (!useModulePerSourceSet()) {
            super.populateModuleDependencies(gradleModule, ideModule, ideProject)
        }

        addTransitiveDependenciesOnImplementedModules(gradleModule, ideModule, ideProject)
        addImplementedModuleNames(gradleModule, ideModule, ideProject, gradleModel)

        if (useModulePerSourceSet()) {
            super.populateModuleDependencies(gradleModule, ideModule, ideProject)
        }
        LOG.logDebugIfEnabled("Finish populating module dependencies. Gradle module: [$gradleModule], Ide module: [$ideModule], Ide project: [$ideProject]")
    }

    private fun addImplementedModuleNames(
        gradleModule: IdeaModule,
        dependentModule: DataNode<ModuleData>,
        ideProject: DataNode<ProjectData>,
        gradleModel: KotlinGradleModel
    ) {
        val implementedModules = gradleModel.implements.mapNotNull { findModuleById(ideProject, gradleModule, it) }
        val kotlinGradleProjectDataNode = dependentModule.kotlinGradleProjectDataNodeOrFail
        val kotlinGradleProjectData = kotlinGradleProjectDataNode.data
        val kotlinGradleSourceSetDataList = kotlinGradleProjectDataNode.kotlinGradleSourceSetDataNodes.map { it.data }
        if (useModulePerSourceSet() && kotlinGradleProjectData.hasKotlinPlugin) {
            val dependentSourceSets = dependentModule.getSourceSetsMap()
            val implementedSourceSetMaps = implementedModules.map { it.getSourceSetsMap() }
            for ((sourceSetName, _) in dependentSourceSets) {
                kotlinGradleSourceSetDataList.find { it.sourceSetName == sourceSetName }?.implementedModuleNames =
                    implementedSourceSetMaps.mapNotNull { it[sourceSetName]?.data?.internalName }
            }
        } else {
            kotlinGradleProjectData.implementedModuleNames = implementedModules.map { it.data.internalName }
        }
    }


    private fun findModuleById(ideProject: DataNode<ProjectData>, gradleModule: IdeaModule, moduleId: String): DataNode<ModuleData>? {
        val isCompositeProject = resolverCtx.models.ideaProject != gradleModule.project
        val compositePrefix =
            if (isCompositeProject && moduleId.startsWith(":")) gradleModule.project.name
            else ""

        val fullModuleId = compositePrefix + moduleId

        @Suppress("UNCHECKED_CAST")
        return ideProject.children.find { (it.data as? ModuleData)?.id == fullModuleId } as DataNode<ModuleData>?
    }

    override fun populateModuleContentRoots(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        nextResolver.populateModuleContentRoots(gradleModule, ideModule)
        val moduleNamePrefix = GradleProjectResolverUtil.getModuleId(resolverCtx, gradleModule)
        resolverCtx.getExtraProject(gradleModule, KotlinGradleModel::class.java)?.let { gradleModel ->
            KotlinGradleFUSLogger.populateGradleUserDir(gradleModel.gradleUserHome)

            val gradleSourceSets = ExternalSystemApiUtil.findAll(ideModule, GradleSourceSetData.KEY)
            for (gradleSourceSetNode in gradleSourceSets) {
                val propertiesForSourceSet =
                    gradleModel.kotlinTaskProperties.filter { (k, _) -> gradleSourceSetNode.data.id == "$moduleNamePrefix:$k" }
                        .toList().singleOrNull()
                gradleSourceSetNode.children.forEach { dataNode ->
                    val data = dataNode.data as? ContentRootData
                    if (data != null) {
                        /*
                        Code snippet for setting in content root properties
                        if (propertiesForSourceSet?.second?.pureKotlinSourceFolders?.contains(File(data.rootPath)) == true) {
                            @Suppress("UNCHECKED_CAST")
                            (dataNode as DataNode<ContentRootData>).isPureKotlinSourceFolder = true
                        }*/
                        val packagePrefix = propertiesForSourceSet?.second?.packagePrefix
                        if (packagePrefix != null) {
                            ExternalSystemSourceType.values().filter { !(it.isResource || it.isGenerated) }.forEach { type ->
                                val paths = data.getPaths(type)
                                val newPaths = paths.map { ContentRootData.SourceRoot(it.path, packagePrefix) }
                                paths.clear()
                                paths.addAll(newPaths)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(PsiPrecedences::class.java)

        private fun Logger.logDebugIfEnabled(message: String) {
            if (isDebugEnabled) debug(message)
        }

        private fun DataNode<ModuleData>.getSourceSetsMap() =
            ExternalSystemApiUtil.getChildren(this, GradleSourceSetData.KEY).associateBy { it.sourceSetName }

        private val DataNode<out ModuleData>.sourceSetName
            get() = (data as? GradleSourceSetData)?.id?.substringAfterLast(':')

        private fun addDependency(ideModule: DataNode<out ModuleData>, targetModule: DataNode<out ModuleData>) {
            val moduleDependencyData = ModuleDependencyData(ideModule.data, targetModule.data)
            moduleDependencyData.scope = DependencyScope.COMPILE
            moduleDependencyData.isExported = false
            moduleDependencyData.isProductionOnTestDependency = targetModule.sourceSetName == "test"
            ideModule.createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData)
        }

        private var DataNode<out ModuleData>.dependencyCacheFallback by NotNullableCopyableDataNodeUserDataProperty(
            Key.create<MutableMap<DataNode<ProjectData>, Collection<DataNode<out ModuleData>>>>("MODULE_DEPENDENCIES_CACHE"),
            hashMapOf()
        )
    }
}
