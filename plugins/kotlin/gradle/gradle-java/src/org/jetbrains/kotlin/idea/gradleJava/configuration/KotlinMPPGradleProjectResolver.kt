// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.build.events.MessageEvent
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.normalizePath
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.toCanonicalPath
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.PlatformUtils
import com.intellij.util.SmartList
import com.intellij.util.containers.MultiMap
import com.intellij.util.text.VersionComparatorUtil
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.idea.IdeaContentRoot
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.ManualLanguageFeatureSetting
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.config.ExternalSystemNativeMainRunTask
import org.jetbrains.kotlin.config.ExternalSystemRunTask
import org.jetbrains.kotlin.config.ExternalSystemTestRunTask
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.PlatformVersion
import org.jetbrains.kotlin.idea.gradle.configuration.*
import org.jetbrains.kotlin.idea.gradle.configuration.GradlePropertiesFileFacade.Companion.KOTLIN_NOT_IMPORTED_COMMON_SOURCE_SETS_SETTING
import org.jetbrains.kotlin.idea.gradle.configuration.utils.UnsafeTestSourceSetHeuristicApi
import org.jetbrains.kotlin.idea.gradle.configuration.utils.predictedProductionSourceSetName
import org.jetbrains.kotlin.idea.gradle.ui.notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.createKotlinMppPopulateModuleDependenciesContext
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.getCompilations
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.populateModuleDependenciesByCompilations
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.populateModuleDependenciesBySourceSetVisibilityGraph
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.fullName
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.getKotlinModuleId
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModelBuilder
import org.jetbrains.kotlin.idea.gradleTooling.arguments.CachedExtractedArgsInfo
import org.jetbrains.kotlin.idea.gradleTooling.arguments.CachedSerializedArgsInfo
import org.jetbrains.kotlin.idea.platform.IdePlatformKindTooling
import org.jetbrains.kotlin.idea.projectModel.*
import org.jetbrains.kotlin.idea.util.NotNullableCopyableDataNodeUserDataProperty
import org.jetbrains.kotlin.util.removeSuffixIfPresent
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult
import org.jetbrains.plugins.gradle.model.*
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver.CONFIGURATION_ARTIFACTS
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver.MODULES_OUTPUTS
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil.getModuleId
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.lang.reflect.Proxy
import java.util.*
import java.util.stream.Collectors

@Order(ExternalSystemConstants.UNORDERED + 1)
open class KotlinMPPGradleProjectResolver : AbstractProjectResolverExtension() {
    private val cacheManager = KotlinMPPCompilerArgumentsCacheMergeManager

    override fun createModule(gradleModule: IdeaModule, projectDataNode: DataNode<ProjectData>): DataNode<ModuleData>? {
        return super.createModule(gradleModule, projectDataNode)?.also {
            cacheManager.mergeCache(gradleModule, resolverCtx)
            initializeModuleData(gradleModule, it, projectDataNode, resolverCtx)
            populateSourceSetInfos(gradleModule, it, resolverCtx)
        }
    }

    override fun getToolingExtensionsClasses(): Set<Class<out Any>> {
        return setOf(KotlinMPPGradleModelBuilder::class.java, KotlinTarget::class.java, Unit::class.java)
    }

    override fun getExtraProjectModelClasses(): Set<Class<out Any>> {
        return setOf(KotlinMPPGradleModel::class.java, KotlinTarget::class.java)
    }

    override fun getExtraCommandLineArgs(): List<String> =
        /**
         * The Kotlin Gradle plugin might want to use this intransitive metadata configuration to tell the IDE, that specific
         * dependencies shall not be passed on to dependsOn source sets. (e.g. some commonized libraries).
         * By default, the Gradle plugin does not use this configuration and instead places the dependencies into a previously
         * supported configuration.
         * This will tell the Gradle plugin that this version of the IDE plugin does support importing this special configuraiton.
         */
        listOf("-Pkotlin.mpp.enableIntransitiveMetadataConfiguration=true")

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        if (ExternalSystemApiUtil.find(ideModule, BuildScriptClasspathData.KEY) == null) {
            val buildScriptClasspathData = buildClasspathData(gradleModule, resolverCtx)
            ideModule.createChild(BuildScriptClasspathData.KEY, buildScriptClasspathData)
        }
        super.populateModuleExtraModels(gradleModule, ideModule)
    }

    override fun populateModuleContentRoots(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        val mppModel = resolverCtx.getMppModel(gradleModule)
        if (mppModel == null) {
            return super.populateModuleContentRoots(gradleModule, ideModule)
        } else {
            if (!nativeDebugAdvertised && mppModel.kotlinNativeHome.isNotEmpty()) {
                nativeDebugAdvertised = true
                suggestNativeDebug(resolverCtx.projectPath)
            }
            if (!kotlinJsInspectionPackAdvertised && mppModel.targets.any { it.platform == KotlinPlatform.JS }) {
                kotlinJsInspectionPackAdvertised = true
                suggestKotlinJsInspectionPackPlugin(resolverCtx.projectPath)
            }
            if (!resolverCtx.isResolveModulePerSourceSet && !PlatformVersion.isAndroidStudio() && !PlatformUtils.isMobileIde() &&
                !PlatformUtils.isAppCode()
            ) {
                notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(resolverCtx.projectPath)
                resolverCtx.report(MessageEvent.Kind.WARNING, ResolveModulesPerSourceSetInMppBuildIssue())
            }
        }
        populateContentRoots(gradleModule, ideModule, resolverCtx)
    }

    override fun populateModuleCompileOutputSettings(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        if (resolverCtx.getExtraProject(gradleModule, KotlinMPPGradleModel::class.java) == null) {
            super.populateModuleCompileOutputSettings(gradleModule, ideModule)
        }

        val mppModel = resolverCtx.getMppModel(gradleModule) ?: return
        val ideaOutDir = File(ideModule.data.linkedExternalProjectPath, "out")
        val projectDataNode = ideModule.getDataNode(ProjectKeys.PROJECT)!!
        val moduleOutputsMap = projectDataNode.getUserData(MODULES_OUTPUTS)!!
        val outputDirs = HashSet<String>()
        getCompilations(gradleModule, mppModel, ideModule, resolverCtx)
            .filterNot { (_, compilation) -> shouldDelegateToOtherPlugin(compilation) }
            .forEach { (dataNode, compilation) ->
                var gradleOutputMap = dataNode.getUserData(GradleProjectResolver.GRADLE_OUTPUTS)
                if (gradleOutputMap == null) {
                    gradleOutputMap = MultiMap.create()
                    dataNode.putUserData(GradleProjectResolver.GRADLE_OUTPUTS, gradleOutputMap)
                }

                val moduleData = dataNode.data

                with(compilation.output) {
                    effectiveClassesDir?.let {
                        moduleData.isInheritProjectCompileOutputPath = false
                        moduleData.setCompileOutputPath(compilation.sourceType, it.absolutePath)
                        for (gradleOutputDir in classesDirs) {
                            recordOutputDir(gradleOutputDir, it, compilation.sourceType, moduleData, moduleOutputsMap, gradleOutputMap)
                        }
                    }
                    resourcesDir?.let {
                        moduleData.setCompileOutputPath(compilation.resourceType, it.absolutePath)
                        recordOutputDir(it, it, compilation.resourceType, moduleData, moduleOutputsMap, gradleOutputMap)
                    }
                }

                dataNode.createChild(KotlinOutputPathsData.KEY, KotlinOutputPathsData(gradleOutputMap.copy()))
            }
        if (outputDirs.any { FileUtil.isAncestor(ideaOutDir, File(it), false) }) {
            excludeOutDir(ideModule, ideaOutDir)
        }
    }

    override fun populateModuleDependencies(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>, ideProject: DataNode<ProjectData>) {
        val mppModel = resolverCtx.getExtraProject(gradleModule, KotlinMPPGradleModel::class.java)
        if (mppModel == null) {
            resolverCtx.getExtraProject(gradleModule, ExternalProject::class.java)?.sourceSets?.values?.forEach { sourceSet ->
                sourceSet.dependencies.modifyDependenciesOnMppModules(ideProject, resolverCtx)
            }

            super.populateModuleDependencies(gradleModule, ideModule, ideProject) //TODO add dependencies on mpp module
        }

        populateModuleDependencies(gradleModule, ideProject, ideModule, resolverCtx)
    }

    private fun recordOutputDir(
        gradleOutputDir: File,
        effectiveOutputDir: File,
        sourceType: ExternalSystemSourceType,
        moduleData: GradleSourceSetData,
        moduleOutputsMap: MutableMap<String, Pair<String, ExternalSystemSourceType>>,
        gradleOutputMap: MultiMap<ExternalSystemSourceType, String>
    ) {
        val gradleOutputPath = toCanonicalPath(gradleOutputDir.absolutePath)
        gradleOutputMap.putValue(sourceType, gradleOutputPath)
        if (gradleOutputDir.path != effectiveOutputDir.path) {
            moduleOutputsMap[gradleOutputPath] = Pair(moduleData.id, sourceType)
        }
    }

    private fun excludeOutDir(ideModule: DataNode<ModuleData>, ideaOutDir: File) {
        val contentRootDataDataNode = ExternalSystemApiUtil.find(ideModule, ProjectKeys.CONTENT_ROOT)

        val excludedContentRootData: ContentRootData
        if (contentRootDataDataNode == null || !FileUtil.isAncestor(File(contentRootDataDataNode.data.rootPath), ideaOutDir, false)) {
            excludedContentRootData = ContentRootData(GradleConstants.SYSTEM_ID, ideaOutDir.absolutePath)
            ideModule.createChild(ProjectKeys.CONTENT_ROOT, excludedContentRootData)
        } else {
            excludedContentRootData = contentRootDataDataNode.data
        }

        excludedContentRootData.storePath(ExternalSystemSourceType.EXCLUDED, ideaOutDir.absolutePath)
    }

    companion object {
        val MPP_CONFIGURATION_ARTIFACTS =
            Key.create<MutableMap<String/* artifact path */, MutableList<String> /* module ids*/>>("gradleMPPArtifactsMap")
        val proxyObjectCloningCache = WeakHashMap<Any, Any>()

        //flag for avoid double resolve from KotlinMPPGradleProjectResolver and KotlinAndroidMPPGradleProjectResolver
        private var DataNode<ModuleData>.isMppDataInitialized
                by NotNullableCopyableDataNodeUserDataProperty(Key.create<Boolean>("IS_MPP_DATA_INITIALIZED"), false)

        private var nativeDebugAdvertised = false
        private var kotlinJsInspectionPackAdvertised = false

        private fun ExternalDependency.getDependencyArtifacts(): Collection<File> =
            when (this) {
                is ExternalProjectDependency -> this.projectDependencyArtifacts
                is FileCollectionDependency -> this.files
                else -> emptyList()
            }

        private fun getOrCreateAffiliatedArtifactsMap(ideProject: DataNode<ProjectData>): Map<String, List<String>>? {
            val mppArtifacts = ideProject.getUserData(MPP_CONFIGURATION_ARTIFACTS) ?: return null
            val configArtifacts = ideProject.getUserData(CONFIGURATION_ARTIFACTS) ?: return null
            // All MPP modules are already known, we can fill configurations map
            return /*ideProject.getUserData(MPP_AFFILATED_ARTIFACTS) ?:*/ HashMap<String, MutableList<String>>().also { newMap ->
                mppArtifacts.forEach { (filePath, moduleIds) ->
                    val list2add = ArrayList<String>()
                    newMap[filePath] = list2add
                    for ((index, module) in moduleIds.withIndex()) {
                        if (index == 0) {
                            configArtifacts[filePath] = module
                        } else {
                            val affiliatedFileName = "$filePath-MPP-$index"
                            configArtifacts[affiliatedFileName] = module
                            list2add.add(affiliatedFileName)
                        }
                    }
                }
                //ideProject.putUserData(MPP_AFFILATED_ARTIFACTS, newMap)
            }
        }

        // TODO move?
        internal fun Collection<ExternalDependency>.modifyDependenciesOnMppModules(
            ideProject: DataNode<ProjectData>,
            resolverCtx: ProjectResolverContext
        ) {
            // Add mpp-artifacts into map used for dependency substitution
            val affiliatedArtifacts = getOrCreateAffiliatedArtifactsMap(ideProject)
            if (affiliatedArtifacts != null) {
                this.forEach { dependency ->
                    val existingArtifactDependencies = dependency.getDependencyArtifacts().map { normalizePath(it.absolutePath) }
                    val dependencies2add = existingArtifactDependencies.flatMap { affiliatedArtifacts[it] ?: emptyList() }
                        .filter { !existingArtifactDependencies.contains(it) }
                    dependencies2add.forEach {
                        dependency.addDependencyArtifactInternal(File(it))
                    }
                }
            }
        }

        private fun ExternalDependency.addDependencyArtifactInternal(file: File) {
            when (this) {
                is DefaultExternalProjectDependency -> this.projectDependencyArtifacts =
                    ArrayList<File>(this.projectDependencyArtifacts).also {
                        it.add(file)
                    }

                is ExternalProjectDependency -> try {
                    this.projectDependencyArtifacts.add(file)
                } catch (_: Exception) {
                    // ignore
                }

                is FileCollectionDependency -> this.files.add(file)
            }
        }

        private fun populateSourceSetInfos(
            gradleModule: IdeaModule,
            mainModuleNode: DataNode<ModuleData>,
            resolverCtx: ProjectResolverContext
        ) {
            val mainModuleData = mainModuleNode.data
            val mainModuleConfigPath = mainModuleData.linkedExternalProjectPath
            val mainModuleFileDirectoryPath = mainModuleData.moduleFileDirectoryPath

            val externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject::class.java) ?: return
            val mppModel = resolverCtx.getMppModel(gradleModule) ?: return
            val projectDataNode = ExternalSystemApiUtil.findParent(mainModuleNode, ProjectKeys.PROJECT) ?: return

            val moduleGroup: Array<String>? = if (!resolverCtx.isUseQualifiedModuleNames) {
                val gradlePath = gradleModule.gradleProject.path
                val isRootModule = gradlePath.isEmpty() || gradlePath == ":"
                if (isRootModule) {
                    arrayOf(mainModuleData.internalName)
                } else {
                    gradlePath.split(":").drop(1).toTypedArray()
                }
            } else null

            val sourceSetMap = projectDataNode.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS)!!

            val sourceSetToRunTasks = calculateRunTasks(mppModel, gradleModule, resolverCtx)

            val sourceSetToCompilationData = LinkedHashMap<String, MutableSet<GradleSourceSetData>>()
            for (target in mppModel.targets) {
                if (shouldDelegateToOtherPlugin(target)) continue
                if (target.name == KotlinTarget.METADATA_TARGET_NAME) continue
                val targetData = KotlinTargetData(target.name).also {
                    it.archiveFile = target.jar?.archiveFile
                    it.konanArtifacts = target.konanArtifacts
                }
                mainModuleNode.createChild(KotlinTargetData.KEY, targetData)

                val compilationIds = LinkedHashSet<String>()
                for (compilation in target.compilations) {
                    val moduleId = getKotlinModuleId(gradleModule, compilation, resolverCtx)
                    val existingSourceSetDataNode = sourceSetMap[moduleId]?.first
                    if (existingSourceSetDataNode?.kotlinSourceSetData?.sourceSetInfo != null) continue

                    compilationIds.add(moduleId)

                    val moduleExternalName = getExternalModuleName(gradleModule, compilation)
                    val moduleInternalName = getInternalModuleName(gradleModule, externalProject, compilation, resolverCtx)

                    val compilationData = existingSourceSetDataNode?.data ?: GradleSourceSetData(
                        moduleId, moduleExternalName, moduleInternalName, mainModuleFileDirectoryPath, mainModuleConfigPath
                    ).also {
                        it.group = externalProject.group
                        it.version = externalProject.version

                        when (compilation.name) {
                            KotlinCompilation.MAIN_COMPILATION_NAME -> {
                                it.publication = ProjectId(externalProject.group, externalProject.name, externalProject.version)
                            }

                            KotlinCompilation.TEST_COMPILATION_NAME -> {
                                it.productionModuleId = getInternalModuleName(
                                    gradleModule,
                                    externalProject,
                                    compilation,
                                    resolverCtx,
                                    KotlinCompilation.MAIN_COMPILATION_NAME
                                )
                            }
                        }

                        it.ideModuleGroup = moduleGroup
                        it.sdkName = gradleModule.jdkNameIfAny

                    }

                    val kotlinSourceSet = createSourceSetInfo(
                        compilation,
                        gradleModule,
                        resolverCtx
                    ) ?: continue
                    kotlinSourceSet.externalSystemRunTasks =
                        compilation.declaredSourceSets.firstNotNullResult { sourceSetToRunTasks[it] } ?: emptyList()

                    /*if (compilation.platform == KotlinPlatform.JVM || compilation.platform == KotlinPlatform.ANDROID) {
                        compilationData.targetCompatibility = (kotlinSourceSet.compilerArguments as? K2JVMCompilerArguments)?.jvmTarget
                    } else */if (compilation.platform == KotlinPlatform.NATIVE) {
                        // Kotlin/Native target has been added to KotlinNativeCompilation only in 1.3.60,
                        // so 'nativeExtensions' may be null in 1.3.5x or earlier versions
                        compilation.nativeExtensions?.konanTarget?.let { konanTarget ->
                            compilationData.konanTargets = setOf(konanTarget)
                        }
                    }

                    for (sourceSet in compilation.declaredSourceSets) {
                        sourceSetToCompilationData.getOrPut(sourceSet.name) { LinkedHashSet() } += compilationData
                        for (dependentSourceSetName in sourceSet.allDependsOnSourceSets) {
                            sourceSetToCompilationData.getOrPut(dependentSourceSetName) { LinkedHashSet() } += compilationData
                        }
                    }

                    val compilationDataNode =
                        (existingSourceSetDataNode ?: mainModuleNode.createChild(GradleSourceSetData.KEY, compilationData)).also {
                            it.addChild(DataNode(KotlinSourceSetData.KEY, KotlinSourceSetData(kotlinSourceSet), it))
                        }
                    if (existingSourceSetDataNode == null) {
                        sourceSetMap[moduleId] = Pair(compilationDataNode, createExternalSourceSet(compilation, compilationData, mppModel))
                    }
                }

                targetData.moduleIds = compilationIds
            }

            val ignoreCommonSourceSets by lazy { externalProject.notImportedCommonSourceSets() }
            for (sourceSet in mppModel.sourceSetsByName.values) {
                if (shouldDelegateToOtherPlugin(sourceSet)) continue
                val platform = sourceSet.actualPlatforms.platforms.singleOrNull()
                if (platform == KotlinPlatform.COMMON && ignoreCommonSourceSets) continue
                val moduleId = getKotlinModuleId(gradleModule, sourceSet, resolverCtx)
                val existingSourceSetDataNode = sourceSetMap[moduleId]?.first
                if (existingSourceSetDataNode?.kotlinSourceSetData != null) continue

                val moduleExternalName = getExternalModuleName(gradleModule, sourceSet)
                val moduleInternalName = getInternalModuleName(gradleModule, externalProject, sourceSet, resolverCtx)

                val sourceSetData = existingSourceSetDataNode?.data ?: GradleSourceSetData(
                    moduleId, moduleExternalName, moduleInternalName, mainModuleFileDirectoryPath, mainModuleConfigPath
                ).also {
                    it.group = externalProject.group
                    it.version = externalProject.version

                    // TODO NOW: Use TestSourceSetUtil instead!

                    if (sourceSet.isTestComponent) {
                        it.productionModuleId = getInternalModuleName(
                            gradleModule,
                            externalProject,
                            sourceSet,
                            resolverCtx,
                            @OptIn(UnsafeTestSourceSetHeuristicApi::class)
                            predictedProductionSourceSetName(sourceSet.name)
                        )
                    } else {
                        if (platform == KotlinPlatform.COMMON) {
                            val artifacts = externalProject.artifactsByConfiguration["metadataApiElements"]?.toMutableList()
                            if (artifacts != null) {
                                it.artifacts = artifacts
                            }
                        }
                    }

                    it.ideModuleGroup = moduleGroup

                    sourceSetToCompilationData[sourceSet.name]?.let { compilationDataRecords ->
                        it.targetCompatibility = compilationDataRecords
                            .mapNotNull { compilationData -> compilationData.targetCompatibility }
                            .minWithOrNull(VersionComparatorUtil.COMPARATOR)

                        if (sourceSet.actualPlatforms.singleOrNull() == KotlinPlatform.NATIVE) {
                            it.konanTargets = compilationDataRecords
                                .flatMap { compilationData -> compilationData.konanTargets }
                                .toSet()
                        }
                    }
                }

                val kotlinSourceSet = createSourceSetInfo(mppModel, sourceSet, gradleModule, resolverCtx) ?: continue
                kotlinSourceSet.externalSystemRunTasks = sourceSetToRunTasks[sourceSet] ?: emptyList()

                val sourceSetDataNode =
                    (existingSourceSetDataNode ?: mainModuleNode.createChild(GradleSourceSetData.KEY, sourceSetData)).also {
                        it.addChild(DataNode(KotlinSourceSetData.KEY, KotlinSourceSetData(kotlinSourceSet), it))
                    }
                if (existingSourceSetDataNode == null) {
                    sourceSetMap[moduleId] = Pair(sourceSetDataNode, createExternalSourceSet(sourceSet, sourceSetData, mppModel))
                }
            }
        }

        private fun initializeModuleData(
            gradleModule: IdeaModule,
            mainModuleNode: DataNode<ModuleData>,
            projectDataNode: DataNode<ProjectData>,
            resolverCtx: ProjectResolverContext
        ) {
            if (mainModuleNode.isMppDataInitialized) return

            val mainModuleData = mainModuleNode.data

            val externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject::class.java)
            val mppModel = resolverCtx.getMppModel(gradleModule)
            if (mppModel == null || externalProject == null) return
            mainModuleNode.isMppDataInitialized = true

            // save artifacts locations.
            val userData = projectDataNode.getUserData(MPP_CONFIGURATION_ARTIFACTS) ?: HashMap<String, MutableList<String>>().apply {
                projectDataNode.putUserData(MPP_CONFIGURATION_ARTIFACTS, this)
            }

            mppModel.targets.filter { it.jar != null && it.jar!!.archiveFile != null }.forEach { target ->
                val path = toCanonicalPath(target.jar!!.archiveFile!!.absolutePath)
                val currentModules = userData[path] ?: ArrayList<String>().apply { userData[path] = this }
                // Test modules should not be added. Otherwise we could get dependnecy of java.mail on jvmTest
                val allSourceSets = target.compilations.filter { !it.isTestComponent }.flatMap { it.declaredSourceSets }.toSet()
                val availableViaDependsOn = allSourceSets.flatMap { it.allDependsOnSourceSets }.mapNotNull { mppModel.sourceSetsByName[it] }
                allSourceSets.union(availableViaDependsOn).forEach { sourceSet ->
                    currentModules.add(getKotlinModuleId(gradleModule, sourceSet, resolverCtx))
                }
            }

            with(projectDataNode.data) {
                if (mainModuleData.linkedExternalProjectPath == linkedExternalProjectPath) {
                    group = mainModuleData.group
                    version = mainModuleData.version
                }
            }
            KotlinGradleProjectData().apply {
                kotlinNativeHome = mppModel.kotlinNativeHome
                coroutines = mppModel.extraFeatures.coroutinesState
                isHmpp = mppModel.extraFeatures.isHMPPEnabled
                kotlinImportingDiagnosticsContainer = mppModel.kotlinImportingDiagnostics
                mainModuleNode.createChild(KotlinGradleProjectData.KEY, this)
            }
            //TODO improve passing version of used multiplatform
        }

        private fun calculateRunTasks(
            mppModel: KotlinMPPGradleModel,
            gradleModule: IdeaModule,
            resolverCtx: ProjectResolverContext
        ): Map<KotlinSourceSet, Collection<ExternalSystemRunTask>> {
            val sourceSetToRunTasks: MutableMap<KotlinSourceSet, MutableCollection<ExternalSystemRunTask>> = HashMap()
            val dependsOnReverseGraph: MutableMap<String, MutableSet<KotlinSourceSet>> = HashMap()
            mppModel.targets.forEach { target ->
                target.compilations.forEach { compilation ->
                    val testRunTasks = target.testTasksFor(compilation)
                        .map {
                            ExternalSystemTestRunTask(
                                it.taskName,
                                gradleModule.gradleProject.path,
                                target.name
                            )
                        }
                    val nativeMainRunTasks = target.nativeMainRunTasks
                        .filter { task -> task.compilationName == compilation.name }
                        .map {
                            ExternalSystemNativeMainRunTask(
                                it.taskName,
                                getKotlinModuleId(gradleModule, compilation, resolverCtx),
                                target.name,
                                it.entryPoint,
                                it.debuggable
                            )
                        }
                    val allRunTasks = testRunTasks + nativeMainRunTasks
                    compilation.declaredSourceSets.forEach { sourceSet ->
                        sourceSetToRunTasks.getOrPut(sourceSet) { LinkedHashSet() } += allRunTasks
                        mppModel.resolveAllDependsOnSourceSets(sourceSet).forEach { dependentModule ->
                            dependsOnReverseGraph.getOrPut(dependentModule.name) { LinkedHashSet() } += sourceSet
                        }
                    }
                }
            }
            mppModel.sourceSetsByName.forEach { (sourceSetName, sourceSet) ->
                dependsOnReverseGraph[sourceSetName]?.forEach { dependingSourceSet ->
                    sourceSetToRunTasks.getOrPut(sourceSet) { LinkedHashSet() } += sourceSetToRunTasks[dependingSourceSet] ?: emptyList()
                }
            }
            return sourceSetToRunTasks
        }

        fun populateContentRoots(
            gradleModule: IdeaModule,
            ideModule: DataNode<ModuleData>,
            resolverCtx: ProjectResolverContext
        ) {
            val mppModel = resolverCtx.getMppModel(gradleModule) ?: return
            val sourceSetToPackagePrefix = mppModel.targets.flatMap { it.compilations }
                .flatMap { compilation ->
                    compilation.declaredSourceSets.map { sourceSet -> sourceSet.name to compilation.kotlinTaskProperties.packagePrefix }
                }
                .toMap()
            if (resolverCtx.getExtraProject(gradleModule, ExternalProject::class.java) == null) return
            processSourceSets(gradleModule, mppModel, ideModule, resolverCtx) { dataNode, sourceSet ->
                if (dataNode == null || shouldDelegateToOtherPlugin(sourceSet)) return@processSourceSets

                createContentRootData(
                    sourceSet.sourceDirs,
                    sourceSet.sourceType,
                    sourceSetToPackagePrefix[sourceSet.name],
                    dataNode
                )
                createContentRootData(
                    sourceSet.resourceDirs,
                    sourceSet.resourceType,
                    null,
                    dataNode
                )
            }

            for (gradleContentRoot in gradleModule.contentRoots ?: emptySet<IdeaContentRoot?>()) {
                if (gradleContentRoot == null) continue

                val rootDirectory = gradleContentRoot.rootDirectory ?: continue
                val ideContentRoot = ContentRootData(GradleConstants.SYSTEM_ID, rootDirectory.absolutePath).also { ideContentRoot ->
                    (gradleContentRoot.excludeDirectories ?: emptySet()).forEach { file ->
                        ideContentRoot.storePath(ExternalSystemSourceType.EXCLUDED, file.absolutePath)
                    }
                }
                ideModule.createChild(ProjectKeys.CONTENT_ROOT, ideContentRoot)
            }

            val mppModelPureKotlinSourceFolders = mppModel.targets.flatMap { it.compilations }
                .flatMap { it.kotlinTaskProperties.pureKotlinSourceFolders ?: emptyList() }
                .map { it.absolutePath }

            ideModule.kotlinGradleProjectDataOrFail.pureKotlinSourceFolders.addAll(mppModelPureKotlinSourceFolders)
        }

        internal data class CompilationWithDependencies(
            val compilation: KotlinCompilation,
            val substitutedDependencies: List<ExternalDependency>
        ) {
            val konanTarget: String?
                get() = compilation.nativeExtensions?.konanTarget

            val dependencyNames: Map<String, ExternalDependency> by lazy {
                substitutedDependencies.associateBy { it.name.removeSuffixIfPresent(" | $konanTarget") }
            }
        }

        fun populateModuleDependencies(
            gradleModule: IdeaModule,
            ideProject: DataNode<ProjectData>,
            ideModule: DataNode<ModuleData>,
            resolverCtx: ProjectResolverContext
        ) {
            val context = createKotlinMppPopulateModuleDependenciesContext(
                gradleModule = gradleModule,
                ideProject = ideProject,
                ideModule = ideModule,
                resolverCtx = resolverCtx
            ) ?: return
            populateModuleDependenciesByCompilations(context)
            populateModuleDependenciesBySourceSetVisibilityGraph(context)
        }

        internal fun getSiblingKotlinModuleData(
            kotlinComponent: KotlinComponent,
            gradleModule: IdeaModule,
            ideModule: DataNode<ModuleData>,
            resolverCtx: ProjectResolverContext
        ): DataNode<out ModuleData>? {
            val usedModuleId = getKotlinModuleId(gradleModule, kotlinComponent, resolverCtx)
            return ideModule.findChildModuleById(usedModuleId)
        }

        private fun createContentRootData(
            sourceDirs: Set<File>,
            sourceType: ExternalSystemSourceType,
            packagePrefix: String?,
            parentNode: DataNode<*>
        ) {
            for (sourceDir in sourceDirs) {
                val contentRootData = ContentRootData(GradleConstants.SYSTEM_ID, sourceDir.absolutePath)
                contentRootData.storePath(sourceType, sourceDir.absolutePath, packagePrefix)
                parentNode.createChild(ProjectKeys.CONTENT_ROOT, contentRootData)
            }
        }

        private fun processSourceSets(
            gradleModule: IdeaModule,
            mppModel: KotlinMPPGradleModel,
            ideModule: DataNode<ModuleData>,
            resolverCtx: ProjectResolverContext,
            processor: (DataNode<GradleSourceSetData>?, KotlinSourceSet) -> Unit
        ) {
            val sourceSetsMap = HashMap<String, DataNode<GradleSourceSetData>>()
            for (dataNode in ExternalSystemApiUtil.findAll(ideModule, GradleSourceSetData.KEY)) {
                if (dataNode.kotlinSourceSetData?.sourceSetInfo != null) {
                    sourceSetsMap[dataNode.data.id] = dataNode
                }
            }
            for (sourceSet in mppModel.sourceSetsByName.values) {
                val moduleId = getKotlinModuleId(gradleModule, sourceSet, resolverCtx)
                val moduleDataNode = sourceSetsMap[moduleId]
                processor(moduleDataNode, sourceSet)
            }
        }

        private val IdeaModule.jdkNameIfAny
            get() = try {
                jdkName
            } catch (e: UnsupportedMethodException) {
                null
            }

        private fun getExternalModuleName(gradleModule: IdeaModule, kotlinComponent: KotlinComponent) =
            gradleModule.name + ":" + kotlinComponent.fullName()

        private fun gradlePathToQualifiedName(
            rootName: String,
            gradlePath: String
        ): String? {
            return ((if (gradlePath.startsWith(":")) "$rootName." else "")
                + Arrays.stream(gradlePath.split(":".toRegex()).toTypedArray())
            .filter { s: String -> s.isNotEmpty() }
            .collect(Collectors.joining(".")))
        }

        private fun getInternalModuleName(
            gradleModule: IdeaModule,
            externalProject: ExternalProject,
            kotlinComponent: KotlinComponent,
            resolverCtx: ProjectResolverContext,
            actualName: String = kotlinComponent.name
        ): String {
            val delimiter: String
            val moduleName = StringBuilder()

            val buildSrcGroup = resolverCtx.buildSrcGroup
            if (resolverCtx.isUseQualifiedModuleNames) {
                delimiter = "."
                if (StringUtil.isNotEmpty(buildSrcGroup)) {
                    moduleName.append(buildSrcGroup).append(delimiter)
                }
                moduleName.append(
                    gradlePathToQualifiedName(
                        gradleModule.project.name,
                        externalProject.qName
                    )
                )
            } else {
                delimiter = "_"
                if (StringUtil.isNotEmpty(buildSrcGroup)) {
                    moduleName.append(buildSrcGroup).append(delimiter)
                }
                moduleName.append(gradleModule.name)
            }
            moduleName.append(delimiter)
            moduleName.append(kotlinComponent.fullName(actualName))
            return PathUtilRt.suggestFileName(moduleName.toString(), true, false)
        }

        private fun createExternalSourceSet(
            compilation: KotlinCompilation,
            compilationData: GradleSourceSetData,
            mppModel: KotlinMPPGradleModel
        ): ExternalSourceSet {
            return DefaultExternalSourceSet().also { sourceSet ->
                val effectiveClassesDir = compilation.output.effectiveClassesDir
                val resourcesDir = compilation.output.resourcesDir

                sourceSet.name = compilation.fullName()
                sourceSet.targetCompatibility = compilationData.targetCompatibility
                sourceSet.dependencies += compilation.dependencies.mapNotNull { mppModel.dependencyMap[it] }
                //TODO after applying patch to IDEA core uncomment the following line:
                // sourceSet.isTest = compilation.sourceSets.filter { isTestModule }.isNotEmpty()
                // It will allow to get rid of hacks with guessing module type in DataServices and obtain properly set productionOnTest flags
                val sourcesWithTypes = SmartList<kotlin.Pair<ExternalSystemSourceType, DefaultExternalSourceDirectorySet>>()
                if (effectiveClassesDir != null) {
                    sourcesWithTypes += compilation.sourceType to DefaultExternalSourceDirectorySet().also { dirSet ->
                        dirSet.outputDir = effectiveClassesDir
                        dirSet.srcDirs = compilation.declaredSourceSets.flatMapTo(LinkedHashSet()) { it.sourceDirs }
                        dirSet.gradleOutputDirs += compilation.output.classesDirs
                        dirSet.setInheritedCompilerOutput(false)
                    }
                }
                if (resourcesDir != null) {
                    sourcesWithTypes += compilation.resourceType to DefaultExternalSourceDirectorySet().also { dirSet ->
                        dirSet.outputDir = resourcesDir
                        dirSet.srcDirs = compilation.declaredSourceSets.flatMapTo(LinkedHashSet()) { it.resourceDirs }
                        dirSet.gradleOutputDirs += resourcesDir
                        dirSet.setInheritedCompilerOutput(false)
                    }
                }

                sourceSet.setSources(sourcesWithTypes.toMap())
            }
        }


        private fun createExternalSourceSet(
            ktSourceSet: KotlinSourceSet,
            ktSourceSetData: GradleSourceSetData,
            mppModel: KotlinMPPGradleModel
        ): ExternalSourceSet {
            return DefaultExternalSourceSet().also { sourceSet ->
                sourceSet.name = ktSourceSet.name
                sourceSet.targetCompatibility = ktSourceSetData.targetCompatibility
                sourceSet.dependencies += ktSourceSet.dependencies.mapNotNull { mppModel.dependencyMap[it] }

                sourceSet.setSources(linkedMapOf(
                    ktSourceSet.sourceType to DefaultExternalSourceDirectorySet().also { dirSet ->
                        dirSet.srcDirs = ktSourceSet.sourceDirs
                    },
                    ktSourceSet.resourceType to DefaultExternalSourceDirectorySet().also { dirSet ->
                        dirSet.srcDirs = ktSourceSet.resourceDirs
                    }
                ).toMap())
            }
        }

        private val KotlinComponent.sourceType
            get() = if (isTestComponent) ExternalSystemSourceType.TEST else ExternalSystemSourceType.SOURCE

        private val KotlinComponent.resourceType
            get() = if (isTestComponent) ExternalSystemSourceType.TEST_RESOURCE else ExternalSystemSourceType.RESOURCE

        @OptIn(ExperimentalGradleToolingApi::class)
        private fun createSourceSetInfo(
            mppModel: KotlinMPPGradleModel,
            sourceSet: KotlinSourceSet,
            gradleModule: IdeaModule,
            resolverCtx: ProjectResolverContext
        ): KotlinSourceSetInfo? {
            if (sourceSet.actualPlatforms.platforms.none { !it.isNotSupported() }) return null
            return KotlinSourceSetInfo(sourceSet).also { info ->
                val languageSettings = sourceSet.languageSettings
                info.moduleId = getKotlinModuleId(gradleModule, sourceSet, resolverCtx)
                info.gradleModuleId = getModuleId(resolverCtx, gradleModule)
                info.actualPlatforms.pushPlatforms(sourceSet.actualPlatforms)
                info.isTestModule = sourceSet.isTestComponent
                info.dependsOn = mppModel.resolveAllDependsOnSourceSets(sourceSet).map { dependsOnSourceSet ->
                    getGradleModuleQualifiedName(resolverCtx, gradleModule, dependsOnSourceSet.name)
                }
                info.additionalVisible = sourceSet.additionalVisibleSourceSets.map { additionalVisibleSourceSetName ->
                    getGradleModuleQualifiedName(resolverCtx, gradleModule, additionalVisibleSourceSetName)
                }.toSet()
                //TODO(auskov): target flours are lost here
                info.lazyCompilerArguments = lazy {
                    createCompilerArguments(emptyList(), sourceSet.actualPlatforms.singleOrNull() ?: KotlinPlatform.COMMON).also {
                        it.multiPlatform = true
                        it.languageVersion = languageSettings.languageVersion
                        it.apiVersion = languageSettings.apiVersion
                        it.progressiveMode = languageSettings.isProgressiveMode
                        it.internalArguments = languageSettings.enabledLanguageFeatures.mapNotNull {
                            val feature = LanguageFeature.fromString(it) ?: return@mapNotNull null
                            val arg = "-XXLanguage:+$it"
                            ManualLanguageFeatureSetting(feature, LanguageFeature.State.ENABLED, arg)
                        }
                        it.optIn = languageSettings.optInAnnotationsInUse.toTypedArray()
                        it.pluginOptions = languageSettings.compilerPluginArguments
                        it.pluginClasspaths = languageSettings.compilerPluginClasspath.map(File::getPath).toTypedArray()
                        it.freeArgs = languageSettings.freeCompilerArgs.toMutableList()
                    }
                }
            }
        }

        @Suppress("DEPRECATION_ERROR")
        // TODO: Unite with other createSourceSetInfo
        // This method is used in Android side of import and it's signature could not be changed
        fun createSourceSetInfo(
            compilation: KotlinCompilation,
            gradleModule: IdeaModule,
            resolverCtx: ProjectResolverContext
        ): KotlinSourceSetInfo? {
            if (compilation.platform.isNotSupported()) return null
            if (Proxy.isProxyClass(compilation.javaClass)) {
                return createSourceSetInfo(
                    KotlinCompilationImpl(compilation, HashMap<Any, Any>()),
                    gradleModule,
                    resolverCtx
                )
            }

            val cacheHolder = CompilerArgumentsCacheMergeManager.compilerArgumentsCacheHolder

            return KotlinSourceSetInfo(compilation).also { sourceSetInfo ->
                sourceSetInfo.moduleId = getKotlinModuleId(gradleModule, compilation, resolverCtx)
                sourceSetInfo.gradleModuleId = getModuleId(resolverCtx, gradleModule)
                sourceSetInfo.actualPlatforms.pushPlatforms(compilation.platform)
                sourceSetInfo.isTestModule = compilation.isTestComponent
                sourceSetInfo.dependsOn = compilation.declaredSourceSets.flatMap { it.allDependsOnSourceSets }.map {
                    getGradleModuleQualifiedName(resolverCtx, gradleModule, it)
                }.distinct().toList()

                sourceSetInfo.additionalVisible = sourceSetInfo.additionalVisible.map {
                    getGradleModuleQualifiedName(resolverCtx, gradleModule, it)
                }.toSet()

                when (val cachedArgsInfo = compilation.cachedArgsInfo) {
                    is CachedExtractedArgsInfo -> {
                        val restoredArgs = lazy { CachedArgumentsRestoring.restoreExtractedArgs(cachedArgsInfo, cacheHolder) }
                        sourceSetInfo.lazyCompilerArguments = lazy { restoredArgs.value.currentCompilerArguments }
                        sourceSetInfo.lazyDefaultCompilerArguments = lazy { restoredArgs.value.defaultCompilerArguments }
                        sourceSetInfo.lazyDependencyClasspath =
                            lazy { restoredArgs.value.dependencyClasspath.map { PathUtil.toSystemIndependentName(it) } }
                    }
                    is CachedSerializedArgsInfo -> {
                        val restoredArgs =
                            lazy { CachedArgumentsRestoring.restoreSerializedArgsInfo(cachedArgsInfo, cacheHolder) }
                        sourceSetInfo.lazyCompilerArguments = lazy {
                            createCompilerArguments(restoredArgs.value.currentCompilerArguments.toList(), compilation.platform).also {
                                it.multiPlatform = true
                            }
                        }
                        sourceSetInfo.lazyDefaultCompilerArguments = lazy {
                            createCompilerArguments(restoredArgs.value.defaultCompilerArguments.toList(), compilation.platform)
                        }

                        sourceSetInfo.lazyDependencyClasspath = lazy {
                            restoredArgs.value.dependencyClasspath.map { PathUtil.toSystemIndependentName(it) }
                        }
                    }
                }
                sourceSetInfo.addSourceSets(compilation.allSourceSets, compilation.fullName(), gradleModule, resolverCtx)
            }
        }

        /** Checks if our IDE doesn't support such platform */
        private fun KotlinPlatform.isNotSupported() = IdePlatformKindTooling.getToolingIfAny(this) == null

        internal fun KotlinSourceSetInfo.addSourceSets(
            sourceSets: Collection<KotlinComponent>,
            selfName: String,
            gradleModule: IdeaModule,
            resolverCtx: ProjectResolverContext
        ) {
            sourceSets
                .asSequence()
                .filter { it.fullName() != selfName }
                .forEach { sourceSetIdsByName[it.name] = getKotlinModuleId(gradleModule, it, resolverCtx) }
        }

        private fun createCompilerArguments(args: List<String>, platform: KotlinPlatform): CommonCompilerArguments {
            val compilerArguments = IdePlatformKindTooling.getTooling(platform).kind.argumentsClass.newInstance()
            parseCommandLineArguments(args.toList(), compilerArguments)
            return compilerArguments
        }

        internal fun getGradleModuleQualifiedName(
            resolverCtx: ProjectResolverContext,
            gradleModule: IdeaModule,
            simpleName: String
        ): String = getModuleId(resolverCtx, gradleModule) + ":" + simpleName

        private fun ExternalProject.notImportedCommonSourceSets() =
            GradlePropertiesFileFacade.forExternalProject(this).readProperty(KOTLIN_NOT_IMPORTED_COMMON_SOURCE_SETS_SETTING)?.equals(
                "true",
                ignoreCase = true
            ) ?: false

        internal fun shouldDelegateToOtherPlugin(compilation: KotlinCompilation): Boolean =
            compilation.platform == KotlinPlatform.ANDROID

        internal fun shouldDelegateToOtherPlugin(kotlinTarget: KotlinTarget): Boolean =
            kotlinTarget.platform == KotlinPlatform.ANDROID

        internal fun shouldDelegateToOtherPlugin(kotlinSourceSet: KotlinSourceSet): Boolean =
            kotlinSourceSet.actualPlatforms.platforms.singleOrNull() == KotlinPlatform.ANDROID
    }
}

private fun KotlinTarget.testTasksFor(compilation: KotlinCompilation) = testRunTasks.filter { task ->
    when (name) {
        "android" -> task.taskName.endsWith(compilation.name, true)
        else -> task.compilationName == compilation.name
    }
}

fun ProjectResolverContext.getMppModel(gradleModule: IdeaModule): KotlinMPPGradleModel? {
    val mppModel = this.getExtraProject(gradleModule, KotlinMPPGradleModel::class.java)
    return if (mppModel is Proxy) {
        this.getExtraProject(gradleModule, KotlinMPPGradleModel::class.java)
            ?.let { kotlinMppModel ->
                KotlinMPPGradleProjectResolver.proxyObjectCloningCache[kotlinMppModel] as? KotlinMPPGradleModelImpl
                    ?: KotlinMPPGradleModelImpl(
                        kotlinMppModel,
                        KotlinMPPGradleProjectResolver.proxyObjectCloningCache
                    ).also {
                        KotlinMPPGradleProjectResolver.proxyObjectCloningCache[kotlinMppModel] = it
                    }
            }
    } else {
        mppModel
    }
}
