// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.tasks.Exec
import org.jetbrains.kotlin.idea.projectModel.*
import java.io.File

class KotlinAndroidSourceSetInfoImpl(
    override val kotlinSourceSetName: String,
    override val androidSourceSetName: String,
    override val androidVariantNames: Set<String>
) : KotlinAndroidSourceSetInfo {
    constructor(info: KotlinAndroidSourceSetInfo) : this(
        kotlinSourceSetName = info.kotlinSourceSetName,
        androidSourceSetName = info.androidSourceSetName,
        androidVariantNames = info.androidVariantNames.toMutableSet()
    )
}

class KotlinSourceSetImpl @OptIn(KotlinGradlePluginVersionDependentApi::class) constructor(
    override val name: String,
    override val languageSettings: KotlinLanguageSettings,
    override val sourceDirs: Set<File>,
    override val resourceDirs: Set<File>,
    override val regularDependencies: Array<KotlinDependencyId>,
    override val intransitiveDependencies: Array<KotlinDependencyId>,
    override val declaredDependsOnSourceSets: Set<String>,
    override val allDependsOnSourceSets: Set<String>,
    override val additionalVisibleSourceSets: Set<String>,
    override val androidSourceSetInfo: KotlinAndroidSourceSetInfo?,
    override val actualPlatforms: KotlinPlatformContainerImpl = KotlinPlatformContainerImpl(),
    override var isTestComponent: Boolean = false,
    override val extras: IdeaKotlinExtras = IdeaKotlinExtras.empty(),
) : KotlinSourceSet {

    override val dependencies: Array<KotlinDependencyId> = regularDependencies + intransitiveDependencies


    @OptIn(KotlinGradlePluginVersionDependentApi::class)
    constructor(kotlinSourceSet: KotlinSourceSet) : this(
        name = kotlinSourceSet.name,
        languageSettings = KotlinLanguageSettingsImpl(kotlinSourceSet.languageSettings),
        sourceDirs = kotlinSourceSet.sourceDirs.toMutableSet(),
        resourceDirs = kotlinSourceSet.resourceDirs.toMutableSet(),
        regularDependencies = kotlinSourceSet.regularDependencies.clone(),
        intransitiveDependencies = kotlinSourceSet.intransitiveDependencies.clone(),
        declaredDependsOnSourceSets = kotlinSourceSet.declaredDependsOnSourceSets.toMutableSet(),
        allDependsOnSourceSets = kotlinSourceSet.allDependsOnSourceSets.toMutableSet(),
        additionalVisibleSourceSets = kotlinSourceSet.additionalVisibleSourceSets.toMutableSet(),
        androidSourceSetInfo = kotlinSourceSet.androidSourceSetInfo?.let(::KotlinAndroidSourceSetInfoImpl),
        extras = IdeaKotlinExtras.copy(kotlinSourceSet.extras),
        actualPlatforms = KotlinPlatformContainerImpl(kotlinSourceSet.actualPlatforms),
        isTestComponent = kotlinSourceSet.isTestComponent
    )

    override fun toString() = name

    init {
        require(allDependsOnSourceSets.containsAll(declaredDependsOnSourceSets)) {
            "Inconsistent source set dependencies: 'allDependsOnSourceSets' is expected to contain all 'declaredDependsOnSourceSets'"
        }
    }
}

data class KotlinLanguageSettingsImpl(
    override val languageVersion: String?,
    override val apiVersion: String?,
    override val isProgressiveMode: Boolean,
    override val enabledLanguageFeatures: Set<String>,
    override val optInAnnotationsInUse: Set<String>,
    override val compilerPluginArguments: Array<String>,
    override val compilerPluginClasspath: Set<File>,
    override val freeCompilerArgs: Array<String>
) : KotlinLanguageSettings {
    constructor(settings: KotlinLanguageSettings) : this(
        languageVersion = settings.languageVersion,
        apiVersion = settings.apiVersion,
        isProgressiveMode = settings.isProgressiveMode,
        enabledLanguageFeatures = settings.enabledLanguageFeatures,
        optInAnnotationsInUse = settings.optInAnnotationsInUse,
        compilerPluginArguments = settings.compilerPluginArguments,
        compilerPluginClasspath = settings.compilerPluginClasspath,
        freeCompilerArgs = settings.freeCompilerArgs
    )
}

data class KotlinCompilationOutputImpl(
    override val classesDirs: Set<File>,
    override val effectiveClassesDir: File?,
    override val resourcesDir: File?
) : KotlinCompilationOutput {
    constructor(output: KotlinCompilationOutput) : this(
        output.classesDirs.toMutableSet(),
        output.effectiveClassesDir,
        output.resourcesDir
    )
}

data class KotlinNativeCompilationExtensionsImpl(
    override val konanTarget: String
) : KotlinNativeCompilationExtensions {
    constructor(extensions: KotlinNativeCompilationExtensions) : this(extensions.konanTarget)
}

data class KotlinCompilationCoordinatesImpl(
    override val targetName: String,
    override val compilationName: String
) : KotlinCompilationCoordinates {
    constructor(coordinates: KotlinCompilationCoordinates) : this(
        targetName = coordinates.targetName,
        compilationName = coordinates.compilationName
    )
}

data class KotlinCompilationImpl(
    override val name: String,
    override val allSourceSets: Set<KotlinSourceSet>,
    override val declaredSourceSets: Set<KotlinSourceSet>,
    override val dependencies: Array<KotlinDependencyId>,
    override val output: KotlinCompilationOutput,
    override val compilerArguments: List<String>?,
    override val kotlinTaskProperties: KotlinTaskProperties,
    override val nativeExtensions: KotlinNativeCompilationExtensions?,
    override val associateCompilations: Set<KotlinCompilationCoordinates>,
    override val extras: IdeaKotlinExtras = IdeaKotlinExtras.empty(),
    override val isTestComponent: Boolean,
) : KotlinCompilation {

    // create deep copy
    constructor(kotlinCompilation: KotlinCompilation, cloningCache: MutableMap<Any, Any>) : this(
        name = kotlinCompilation.name,
        declaredSourceSets = cloneSourceSetsWithCaching(kotlinCompilation.declaredSourceSets, cloningCache),
        allSourceSets = cloneSourceSetsWithCaching(kotlinCompilation.allSourceSets, cloningCache),
        dependencies = kotlinCompilation.dependencies,
        output = KotlinCompilationOutputImpl(kotlinCompilation.output),
        compilerArguments = kotlinCompilation.compilerArguments?.toList(),
        kotlinTaskProperties = KotlinTaskPropertiesImpl(kotlinCompilation.kotlinTaskProperties),
        nativeExtensions = kotlinCompilation.nativeExtensions?.let(::KotlinNativeCompilationExtensionsImpl),
        associateCompilations = cloneCompilationCoordinatesWithCaching(kotlinCompilation.associateCompilations, cloningCache),
        extras = IdeaKotlinExtras.copy(kotlinCompilation.extras),
        isTestComponent = kotlinCompilation.isTestComponent,
    ) {
        disambiguationClassifier = kotlinCompilation.disambiguationClassifier
        platform = kotlinCompilation.platform
    }

    override var disambiguationClassifier: String? = null
        internal set
    override lateinit var platform: KotlinPlatform
        internal set

    override fun toString() = name

    companion object {
        private fun cloneSourceSetsWithCaching(
            sourceSets: Collection<KotlinSourceSet>,
            cloningCache: MutableMap<Any, Any>
        ): Set<KotlinSourceSet> =
            sourceSets.map { initialSourceSet ->
                (cloningCache[initialSourceSet] as? KotlinSourceSet) ?: KotlinSourceSetImpl(initialSourceSet).also {
                    cloningCache[initialSourceSet] = it
                }
            }.toMutableSet()

        private fun cloneCompilationCoordinatesWithCaching(
            coordinates: Set<KotlinCompilationCoordinates>,
            cloningCache: MutableMap<Any, Any>
        ): Set<KotlinCompilationCoordinates> = coordinates.map { initial ->
            cloningCache.getOrPut(initial) { KotlinCompilationCoordinatesImpl(initial) } as KotlinCompilationCoordinates
        }.toMutableSet()
    }
}

data class KotlinTargetJarImpl(
    override val archiveFile: File?,
    override val compilations: Collection<KotlinCompilation>
) : KotlinTargetJar

data class KotlinTargetImpl(
    override val name: String,
    override val presetName: String?,
    override val disambiguationClassifier: String?,
    override val platform: KotlinPlatform,
    override val compilations: Collection<KotlinCompilation>,
    override val testRunTasks: Collection<KotlinTestRunTask>,
    override val nativeMainRunTasks: Collection<KotlinNativeMainRunTask>,
    override val jar: KotlinTargetJar?,
    override val konanArtifacts: List<KonanArtifactModel>,
    override val extras: IdeaKotlinExtras
) : KotlinTarget {
    override fun toString() = name

    constructor(target: KotlinTarget, cloningCache: MutableMap<Any, Any>) : this(
        target.name,
        target.presetName,
        target.disambiguationClassifier,
        KotlinPlatform.byId(target.platform.id) ?: KotlinPlatform.COMMON,
        target.compilations.map { initialCompilation ->
            (cloningCache[initialCompilation] as? KotlinCompilation)
                ?: KotlinCompilationImpl(initialCompilation, cloningCache).also {
                    cloningCache[initialCompilation] = it
                }
        }.toList(),
        target.testRunTasks.map { initialTestTask ->
            (cloningCache[initialTestTask] as? KotlinTestRunTask)
                ?: KotlinTestRunTaskImpl(
                    initialTestTask.taskName,
                    initialTestTask.compilationName
                ).also {
                    cloningCache[initialTestTask] = it
                }
        },
        target.nativeMainRunTasks.map { initialTestTask ->
            (cloningCache[initialTestTask] as? KotlinNativeMainRunTask)
                ?: KotlinNativeMainRunTaskImpl(
                    initialTestTask.taskName,
                    initialTestTask.compilationName,
                    initialTestTask.entryPoint,
                    initialTestTask.debuggable
                ).also {
                    cloningCache[initialTestTask] = it
                }
        },
        KotlinTargetJarImpl(
            target.jar?.archiveFile,
            target.jar?.compilations?.map { initialCompilation ->
                (cloningCache[initialCompilation] as? KotlinCompilation)
                    ?: KotlinCompilationImpl(initialCompilation, cloningCache).also {
                        cloningCache[initialCompilation] = it
                    }
            }.orEmpty(),
        ),
        target.konanArtifacts.map { KonanArtifactModelImpl(it) }.toList(),
        IdeaKotlinExtras.copy(target.extras)
    )
}

data class KotlinTestRunTaskImpl(
    override val taskName: String,
    override val compilationName: String
) : KotlinTestRunTask

data class KotlinNativeMainRunTaskImpl(
    override val taskName: String,
    override val compilationName: String,
    override val entryPoint: String,
    override val debuggable: Boolean
) : KotlinNativeMainRunTask

data class ExtraFeaturesImpl(
    override val coroutinesState: String?,
    override val isHMPPEnabled: Boolean,
) : ExtraFeatures

data class KotlinMPPGradleModelImpl @OptIn(KotlinGradlePluginVersionDependentApi::class) constructor(
    override val sourceSetsByName: Map<String, KotlinSourceSet>,
    override val targets: Collection<KotlinTarget>,
    override val extraFeatures: ExtraFeatures,
    override val kotlinNativeHome: String,
    override val dependencyMap: Map<KotlinDependencyId, KotlinDependency>,
    override val dependencies: IdeaKotlinDependenciesContainer?,
    override val kotlinImportingDiagnostics: KotlinImportingDiagnosticsContainer = mutableSetOf(),
    override val kotlinGradlePluginVersion: KotlinGradlePluginVersion?
) : KotlinMPPGradleModel {

    @OptIn(KotlinGradlePluginVersionDependentApi::class)
    constructor(mppModel: KotlinMPPGradleModel, cloningCache: MutableMap<Any, Any>) : this(
        sourceSetsByName = mppModel.sourceSetsByName.mapValues { initialSourceSet ->
            (cloningCache[initialSourceSet] as? KotlinSourceSet) ?: KotlinSourceSetImpl(initialSourceSet.value)
                .also { cloningCache[initialSourceSet] = it }
        },
        targets = mppModel.targets.map { initialTarget ->
            (cloningCache[initialTarget] as? KotlinTarget) ?: KotlinTargetImpl(initialTarget, cloningCache).also {
                cloningCache[initialTarget] = it
            }
        }.toList(),
        extraFeatures = ExtraFeaturesImpl(
            mppModel.extraFeatures.coroutinesState,
            mppModel.extraFeatures.isHMPPEnabled,
        ),
        kotlinNativeHome = mppModel.kotlinNativeHome,
        dependencyMap = mppModel.dependencyMap.map { it.key to it.value.deepCopy(cloningCache) }.toMap(),
        dependencies = mppModel.dependencies,
        kotlinImportingDiagnostics = mppModel.kotlinImportingDiagnostics.mapTo(mutableSetOf()) { it.deepCopy(cloningCache) },
        kotlinGradlePluginVersion = mppModel.kotlinGradlePluginVersion?.reparse()
    )
}

class KotlinPlatformContainerImpl() : KotlinPlatformContainer {
    private val defaultCommonPlatform = setOf(KotlinPlatform.COMMON)
    private var myPlatforms: MutableSet<KotlinPlatform>? = null

    override val arePlatformsInitialized: Boolean
        get() = myPlatforms != null

    constructor(platform: KotlinPlatformContainer) : this() {
        myPlatforms = platform.platforms.toMutableSet()
    }

    override val platforms: Set<KotlinPlatform>
        get() = myPlatforms ?: defaultCommonPlatform

    override fun pushPlatforms(platforms: Iterable<KotlinPlatform>) {
        myPlatforms = (myPlatforms ?: LinkedHashSet()).apply {
            addAll(platforms)
            if (contains(KotlinPlatform.COMMON)) {
                clear()
                addAll(defaultCommonPlatform)
            }
        }
    }
}

data class KonanArtifactModelImpl(
    override val targetName: String,
    override val executableName: String,
    override val type: String,
    override val targetPlatform: String,
    override val file: File,
    override val buildTaskPath: String,
    override val runConfiguration: KonanRunConfigurationModel,
    override val isTests: Boolean,
    override val freeCompilerArgs: Array<String>? = emptyArray(), // nullable for backwards compatibility
    override val exportDependencies: Array<KotlinDependencyId>? = emptyArray(), // nullable for backwards compatibility
    override val binaryOptions: Array<String>? = emptyArray(), // nullable for backwards compatibility
) : KonanArtifactModel {
    constructor(artifact: KonanArtifactModel) : this(
        artifact.targetName,
        artifact.executableName,
        artifact.type,
        artifact.targetPlatform,
        artifact.file,
        artifact.buildTaskPath,
        KonanRunConfigurationModelImpl(artifact.runConfiguration),
        artifact.isTests,
        checkNotNull(artifact.freeCompilerArgs) { "free compiler arguments are unexpectedly null" },
        checkNotNull(artifact.exportDependencies) { "export dependencies are unexpectedly null" },
        checkNotNull(artifact.binaryOptions) { "binary compiler options are unexpectedly null" },
    )
}

data class KonanRunConfigurationModelImpl(
    override val workingDirectory: String,
    override val programParameters: List<String>,
    override val environmentVariables: Map<String, String>
) : KonanRunConfigurationModel {
    constructor(configuration: KonanRunConfigurationModel) : this(
        configuration.workingDirectory,
        ArrayList(configuration.programParameters),
        HashMap(configuration.environmentVariables)
    )

    constructor(runTask: Exec?) : this(
        runTask?.workingDir?.path ?: KonanRunConfigurationModel.NO_WORKING_DIRECTORY,
        runTask?.args as List<String>? ?: KonanRunConfigurationModel.NO_PROGRAM_PARAMETERS,
        (runTask?.environment as Map<String, Any>?)
            ?.mapValues { it.value.toString() } ?: KonanRunConfigurationModel.NO_ENVIRONMENT_VARIABLES
    )
}
