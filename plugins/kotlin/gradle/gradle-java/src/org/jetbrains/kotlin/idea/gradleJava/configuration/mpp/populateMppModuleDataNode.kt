// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.SmartList
import com.intellij.util.text.VersionComparatorUtil
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.ManualLanguageFeatureSetting
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.IdePlatformKindTooling
import org.jetbrains.kotlin.idea.gradle.configuration.*
import org.jetbrains.kotlin.idea.gradle.configuration.utils.UnsafeTestSourceSetHeuristicApi
import org.jetbrains.kotlin.idea.gradle.configuration.utils.predictedProductionSourceSetName
import org.jetbrains.kotlin.idea.gradleJava.configuration.*
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.KotlinMppGradleProjectResolverExtension.Result.Skip
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.KotlinModuleUtils
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.KotlinModuleUtils.fullName
import org.jetbrains.kotlin.idea.gradleTooling.KotlinCompilationImpl
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.resolveAllDependsOnSourceSets
import org.jetbrains.kotlin.idea.projectModel.*
import org.jetbrains.kotlin.idea.util.NotNullableCopyableDataNodeUserDataProperty
import org.jetbrains.kotlin.platform.impl.JsIdePlatformKind
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.kotlin.platform.impl.NativeIdePlatformKind
import org.jetbrains.kotlin.platform.impl.WasmIdePlatformKind
import org.jetbrains.plugins.gradle.model.DefaultExternalSourceDirectorySet
import org.jetbrains.plugins.gradle.model.DefaultExternalSourceSet
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.io.File
import java.lang.reflect.Proxy
import java.util.*
import java.util.stream.Collectors

/**
 * Creates and adds [GradleSourceSetData] nodes and [KotlinSourceSetInfo] for the given [moduleDataNode]
 * @param moduleDataNode: The node representing a specific Gradle project which contains multiplatform source sets
 */
internal fun populateMppModuleDataNode(context: KotlinMppGradleProjectResolver.Context) {
    context.initializeModuleData()
    context.createMppGradleSourceSetDataNodes()
}

internal fun shouldDelegateToOtherPlugin(compilation: KotlinCompilation): Boolean =
    compilation.platform == KotlinPlatform.ANDROID

internal fun shouldDelegateToOtherPlugin(kotlinSourceSet: KotlinSourceSet): Boolean =
    kotlinSourceSet.actualPlatforms.platforms.singleOrNull() == KotlinPlatform.ANDROID

internal fun doCreateSourceSetInfo(
    mppModel: KotlinMPPGradleModel,
    sourceSet: KotlinSourceSet,
    gradleModule: IdeaModule,
    resolverCtx: ProjectResolverContext,
): KotlinSourceSetInfo? {
    if (sourceSet.actualPlatforms.platforms.none { !it.isNotSupported() }) return null
    return KotlinSourceSetInfo(sourceSet).also { info ->
        val languageSettings = sourceSet.languageSettings
        info.moduleId = KotlinModuleUtils.getKotlinModuleId(gradleModule, sourceSet, resolverCtx)
        info.gradleModuleId = GradleProjectResolverUtil.getModuleId(resolverCtx, gradleModule)
        info.actualPlatforms.pushPlatforms(sourceSet.actualPlatforms)
        info.isTestModule = sourceSet.isTestComponent
        info.dependsOn = mppModel.resolveAllDependsOnSourceSets(sourceSet).map { dependsOnSourceSet ->
            KotlinModuleUtils.getGradleModuleQualifiedName(resolverCtx, gradleModule, dependsOnSourceSet.name)
        }.toSet()
        info.additionalVisible = sourceSet.additionalVisibleSourceSets.map { additionalVisibleSourceSetName ->
            KotlinModuleUtils.getGradleModuleQualifiedName(resolverCtx, gradleModule, additionalVisibleSourceSetName)
        }.toSet()

        // More precise computation of KotlinPlatform is required in the case of projects
        // with enabled HMPP and Android + JVM targets.
        // Early, for common source set in such project the K2MetadataCompilerArguments instance
        // was creating, since `sourceSet.actualPlatforms.platforms` contains more then 1 KotlinPlatform.
        val platformKinds = sourceSet.actualPlatforms.platforms
            .map { IdePlatformKindTooling.getTooling(it).kind }
            .toSet()
        val compilerArgumentsPlatform = platformKinds.singleOrNull()?.let {
            when (it) {
                is JvmIdePlatformKind -> KotlinPlatform.JVM
                is JsIdePlatformKind -> KotlinPlatform.JS
                is WasmIdePlatformKind -> KotlinPlatform.WASM
                is NativeIdePlatformKind -> KotlinPlatform.NATIVE
                else -> KotlinPlatform.COMMON
            }
        } ?: KotlinPlatform.COMMON

        info.compilerArguments = CompilerArgumentsProvider {
            createCompilerArguments(emptyList(), compilerArgumentsPlatform).also {
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

// TODO: Unite with other createSourceSetInfo
internal fun doCreateSourceSetInfo(
    model: KotlinMPPGradleModel,
    compilation: KotlinCompilation,
    gradleModule: IdeaModule,
    resolverCtx: ProjectResolverContext
): KotlinSourceSetInfo? {
    if (compilation.platform.isNotSupported()) return null
    if (Proxy.isProxyClass(compilation.javaClass)) {
        return doCreateSourceSetInfo(
            model,
            KotlinCompilationImpl(compilation, HashMap<Any, Any>()),
            gradleModule,
            resolverCtx
        )
    }

    return KotlinSourceSetInfo(compilation).also { sourceSetInfo ->
        sourceSetInfo.moduleId = KotlinModuleUtils.getKotlinModuleId(gradleModule, compilation, resolverCtx)
        sourceSetInfo.gradleModuleId = GradleProjectResolverUtil.getModuleId(resolverCtx, gradleModule)
        sourceSetInfo.actualPlatforms.pushPlatforms(compilation.platform)
        sourceSetInfo.isTestModule = compilation.isTestComponent
        sourceSetInfo.dependsOn = model.resolveAllDependsOnSourceSets(compilation.declaredSourceSets)
            .map { dependsOnSourceSet ->
                KotlinModuleUtils.getGradleModuleQualifiedName(
                    resolverCtx,
                    gradleModule,
                    dependsOnSourceSet.name
                )
            }.toSet()

        sourceSetInfo.additionalVisible = sourceSetInfo.additionalVisible.map {
            KotlinModuleUtils.getGradleModuleQualifiedName(resolverCtx, gradleModule, it)
        }.toSet()

        compilation.compilerArguments?.let { compilerArguments ->
            sourceSetInfo.compilerArguments = CompilerArgumentsProvider {
                createCompilerArguments(compilerArguments, compilation.platform)
            }
        }
        sourceSetInfo.addSourceSets(compilation.allSourceSets, compilation.fullName(), gradleModule, resolverCtx)
    }
}

private fun KotlinMppGradleProjectResolver.Context.initializeModuleData() {
    if (moduleDataNode.isMppDataInitialized) return


    /* Populate 'MPP_CONFIGURATION_ARTIFACTS' for every production source set */
    run {
        val externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject::class.java)
        if (externalProject == null) return
        moduleDataNode.isMppDataInitialized = true

        // save artifacts locations.
        val userData = projectDataNode.getUserData(KotlinMppGradleProjectResolver.MPP_CONFIGURATION_ARTIFACTS)
            ?: HashMap<String, MutableList<String>>().apply {
                projectDataNode.putUserData(KotlinMppGradleProjectResolver.MPP_CONFIGURATION_ARTIFACTS, this)
            }

        mppModel.targets.filter { it.jar != null && it.jar!!.archiveFile != null }.forEach { target ->
            val path = ExternalSystemApiUtil.toCanonicalPath(target.jar!!.archiveFile!!.absolutePath)
            val currentModules = userData[path] ?: ArrayList<String>().apply { userData[path] = this }
            val declaredSourceSetsOfCompilations = target.jar!!.compilations.flatMap { it.declaredSourceSets }.toSet()
            val availableViaDependsOn = declaredSourceSetsOfCompilations
                .flatMap { it.allDependsOnSourceSets }
                .mapNotNull { mppModel.sourceSetsByName[it] }
            declaredSourceSetsOfCompilations.union(availableViaDependsOn).forEach { sourceSet ->
                currentModules.add(KotlinModuleUtils.getKotlinModuleId(gradleModule, sourceSet, resolverCtx))
            }
        }
    }

    /* Create KotlinGradleProjectData node and attach it to moduleDataNode */
    run {
        val mainModuleData = moduleDataNode.data
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
            moduleDataNode.createChild(KotlinGradleProjectData.KEY, this)
        }
    }
}

private fun KotlinMppGradleProjectResolver.Context.createMppGradleSourceSetDataNodes() {
    val mainModuleData = moduleDataNode.data
    val mainModuleConfigPath = mainModuleData.linkedExternalProjectPath
    val mainModuleFileDirectoryPath = mainModuleData.moduleFileDirectoryPath

    val extensionInstance = KotlinMppGradleProjectResolverExtension.buildInstance()

    val externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject::class.java) ?: return

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

    val sourceSetToCompilationData = LinkedHashMap<String, MutableSet<GradleSourceSetData>>()
    for (target in mppModel.targets) {
        if (shouldDelegateToOtherPlugin(target)) continue
        if (target.name == KotlinTarget.METADATA_TARGET_NAME) continue
        val targetData = KotlinTargetData(target.name).also {
            it.archiveFile = target.jar?.archiveFile
            it.konanArtifacts = target.konanArtifacts
        }
        moduleDataNode.createChild(KotlinTargetData.KEY, targetData)

        val compilationIds = LinkedHashSet<String>()
        for (compilation in target.compilations) {
            val moduleId = KotlinModuleUtils.getKotlinModuleId(gradleModule, compilation, resolverCtx)
            val existingSourceSetDataNode = sourceSetMap[moduleId]?.first
            if (existingSourceSetDataNode?.kotlinSourceSetData?.sourceSetInfo != null) continue

            /* Execute extensions and do not create any GradleSourceSetData node if any extension wants us to Skip */
            if (extensionInstance.beforeMppGradleSourceSetDataNodeCreation(this, compilation) == Skip) {
                continue
            }

            compilationIds.add(moduleId)

            val moduleExternalName = getExternalModuleName(gradleModule, compilation)
            val moduleInternalName = getInternalModuleName(gradleModule, externalProject, compilation, resolverCtx)

            val compilationData = existingSourceSetDataNode?.data ?: createGradleSourceSetData(
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

            val kotlinSourceSet = doCreateSourceSetInfo(mppModel, compilation, gradleModule, resolverCtx) ?: continue

            if (compilation.platform == KotlinPlatform.NATIVE) {
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
                (existingSourceSetDataNode ?: moduleDataNode.createChild(GradleSourceSetData.KEY, compilationData)).also {
                    it.addChild(DataNode(KotlinSourceSetData.KEY, KotlinSourceSetData(kotlinSourceSet), it))
                }
            if (existingSourceSetDataNode == null) {
                sourceSetMap[moduleId] = Pair(compilationDataNode, createExternalSourceSet(compilation, compilationData, mppModel))
            }

            /* Execution all extensions after we freshly created a GradleSourceSetData node for the given compilation */
            extensionInstance.afterMppGradleSourceSetDataNodeCreated(this, compilation, compilationDataNode)
        }

        targetData.moduleIds = compilationIds
    }

    val ignoreCommonSourceSets by lazy { externalProject.notImportedCommonSourceSets() }
    for (sourceSet in mppModel.sourceSetsByName.values) {
        if (shouldDelegateToOtherPlugin(sourceSet)) continue

        val platform = sourceSet.actualPlatforms.platforms.singleOrNull()
        if (platform == KotlinPlatform.COMMON && ignoreCommonSourceSets) continue
        val moduleId = KotlinModuleUtils.getKotlinModuleId(gradleModule, sourceSet, resolverCtx)
        val existingSourceSetDataNode = sourceSetMap[moduleId]?.first
        if (existingSourceSetDataNode?.kotlinSourceSetData != null) continue

        /* Execute extensions and do not create any GradleSourceSetData node if any extension wants us to Skip */
        if (extensionInstance.beforeMppGradleSourceSetDataNodeCreation(this, sourceSet) == Skip) {
            continue
        }

        val sourceSetData = existingSourceSetDataNode?.data ?: createGradleSourceSetData(
            sourceSet, gradleModule, moduleDataNode, resolverCtx
        ).also {
            it.group = externalProject.group
            it.version = externalProject.version

            if (sourceSet.isTestComponent) {
                it.productionModuleId = getInternalModuleName(
                    gradleModule,
                    externalProject,
                    sourceSet,
                    resolverCtx,
                    @OptIn(UnsafeTestSourceSetHeuristicApi::class)
                    (predictedProductionSourceSetName(sourceSet.name))
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

        val kotlinSourceSet = KotlinMppGradleProjectResolver.createSourceSetInfo(mppModel, sourceSet, gradleModule, resolverCtx) ?: continue

        val sourceSetDataNode = (existingSourceSetDataNode ?: moduleDataNode.createChild(GradleSourceSetData.KEY, sourceSetData)).also {
            it.addChild(DataNode(KotlinSourceSetData.KEY, KotlinSourceSetData(kotlinSourceSet), it))
        }
        if (existingSourceSetDataNode == null) {
            sourceSetMap[moduleId] = Pair(sourceSetDataNode, createExternalSourceSet(sourceSet, sourceSetData, mppModel))
        }

        /* Execution all extensions after we freshly created a GradleSourceSetData node for the given compilation */
        extensionInstance.afterMppGradleSourceSetDataNodeCreated(this, sourceSet, sourceSetDataNode)
    }
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

private fun createGradleSourceSetData(
    sourceSet: KotlinSourceSet,
    gradleModule: IdeaModule,
    mainModuleNode: DataNode<ModuleData>,
    resolverCtx: ProjectResolverContext,
): GradleSourceSetData {
    val moduleId = KotlinModuleUtils.getKotlinModuleId(gradleModule, sourceSet, resolverCtx)
    val moduleExternalName = getExternalModuleName(gradleModule, sourceSet)
    val moduleInternalName = mainModuleNode.data.internalName + "." + sourceSet.fullName()
    val moduleFileDirectoryPath = mainModuleNode.data.moduleFileDirectoryPath
    val linkedExternalProjectPath = mainModuleNode.data.linkedExternalProjectPath

    return GradleSourceSetData(moduleId, moduleExternalName, moduleInternalName, moduleFileDirectoryPath, linkedExternalProjectPath)
}

private fun createGradleSourceSetData(
    moduleId: String,
    moduleExternalName: String,
    moduleInternalName: String,
    mainModuleFileDirectoryPath: String,
    mainModuleConfigPath: String
) = GradleSourceSetData(
    moduleId, moduleExternalName, moduleInternalName, mainModuleFileDirectoryPath, mainModuleConfigPath
)

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

//flag for avoid double resolve from KotlinMPPGradleProjectResolver and KotlinAndroidMPPGradleProjectResolver
private var DataNode<ModuleData>.isMppDataInitialized
        by NotNullableCopyableDataNodeUserDataProperty(Key.create("IS_MPP_DATA_INITIALIZED"), false)

private fun shouldDelegateToOtherPlugin(kotlinTarget: KotlinTarget): Boolean =
    kotlinTarget.platform == KotlinPlatform.ANDROID

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

private fun getExternalModuleName(gradleModule: IdeaModule, kotlinComponent: KotlinComponent) =
    gradleModule.name + ":" + kotlinComponent.fullName()

private fun gradlePathToQualifiedName(
    rootName: String,
    gradlePath: String
): String {
    return ((if (gradlePath.startsWith(":")) "$rootName." else "")
            + Arrays.stream(gradlePath.split(":".toRegex()).toTypedArray())
        .filter { s: String -> s.isNotEmpty() }
        .collect(Collectors.joining(".")))
}

private val IdeaModule.jdkNameIfAny
    get() = try {
        jdkName
    } catch (e: UnsupportedMethodException) {
        null
    }

private fun ExternalProject.notImportedCommonSourceSets() =
    GradlePropertiesFileFacade.forExternalProject(this)
        .readProperty(GradlePropertiesFileFacade.KOTLIN_NOT_IMPORTED_COMMON_SOURCE_SETS_SETTING)?.equals(
            "true",
            ignoreCase = true
        ) ?: false

private fun KotlinPlatform.isNotSupported() = IdePlatformKindTooling.getToolingIfAny(this) == null

fun createCompilerArguments(args: List<String>, platform: KotlinPlatform): CommonCompilerArguments {
    val compilerArguments = IdePlatformKindTooling.getTooling(platform).kind.argumentsClass.newInstance()
    parseCommandLineArguments(args.toList(), compilerArguments)
    return compilerArguments
}

private fun KotlinSourceSetInfo.addSourceSets(
    sourceSets: Collection<KotlinComponent>,
    selfName: String,
    gradleModule: IdeaModule,
    resolverCtx: ProjectResolverContext
) {
    sourceSets
        .asSequence()
        .filter { it.fullName() != selfName }
        .forEach { sourceSetIdsByName[it.name] = KotlinModuleUtils.getKotlinModuleId(gradleModule, it, resolverCtx) }
}
