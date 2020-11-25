package org.jetbrains.kotlin.gradle

internal fun createKotlinMPPGradleModel(
    dependencyMap: Map<KotlinDependencyId, KotlinDependency> = emptyMap(),
    sourceSets: Set<KotlinSourceSet> = emptySet(),
    targets: Iterable<KotlinTarget> = emptyList(),
    extraFeatures: ExtraFeatures = createExtraFeatures(),
    kotlinNativeHome: String = ""

): KotlinMPPGradleModelImpl {
    return KotlinMPPGradleModelImpl(
        dependencyMap = dependencyMap,
        sourceSets = sourceSets.associateBy { it.name },
        targets = targets.toList(),
        extraFeatures = extraFeatures,
        kotlinNativeHome = kotlinNativeHome
    )
}

internal fun createExtraFeatures(
    coroutinesState: String? = null,
    isHmppEnabled: Boolean = false,
    isNativeDependencyPropagationEnabled: Boolean = false
): ExtraFeaturesImpl {
    return ExtraFeaturesImpl(
        coroutinesState = coroutinesState,
        isHMPPEnabled = isHmppEnabled,
        isNativeDependencyPropagationEnabled = isNativeDependencyPropagationEnabled
    )
}

internal fun createKotlinSourceSet(
    name: String,
    dependsOnSourceSets: Set<String> = emptySet(),
    platforms: Set<KotlinPlatform> = emptySet(),
    isTestModule: Boolean = false,
): KotlinSourceSetImpl = KotlinSourceSetImpl(
    name = name,
    languageSettings = KotlinLanguageSettingsImpl(
        languageVersion = null,
        apiVersion = null,
        isProgressiveMode = false,
        enabledLanguageFeatures = emptySet(),
        experimentalAnnotationsInUse = emptySet(),
        compilerPluginArguments = emptyArray(),
        compilerPluginClasspath = emptySet(),
        freeCompilerArgs = emptyArray()
    ),
    sourceDirs = emptySet(),
    resourceDirs = emptySet(),
    dependencies = emptyArray(),
    dependsOnSourceSets = dependsOnSourceSets,
    defaultPlatform = KotlinPlatformContainerImpl().apply { addSimplePlatforms(platforms) },
    defaultIsTestModule = isTestModule
)

internal fun createKotlinCompilation(
    name: String = "main",
    sourceSets: Set<KotlinSourceSet> = emptySet(),
    dependencies: Iterable<KotlinDependencyId> = emptyList(),
    output: KotlinCompilationOutput = createKotlinCompilationOutput(),
    arguments: KotlinCompilationArguments = createKotlinCompilationArguments(),
    dependencyClasspath: Iterable<String> = emptyList(),
    kotlinTaskProperties: KotlinTaskProperties = createKotlinTaskProperties(),
    nativeExtensions: KotlinNativeCompilationExtensions? = null

): KotlinCompilationImpl {
    return KotlinCompilationImpl(
        name = name,
        sourceSets = sourceSets,
        dependencies = dependencies.toList().toTypedArray(),
        output = output,
        arguments = arguments,
        dependencyClasspath = dependencyClasspath.toList().toTypedArray(),
        kotlinTaskProperties = kotlinTaskProperties,
        nativeExtensions = nativeExtensions
    )
}

internal fun createKotlinCompilationOutput(): KotlinCompilationOutputImpl {
    return KotlinCompilationOutputImpl(
        classesDirs = emptySet(),
        effectiveClassesDir = null,
        resourcesDir = null
    )
}

internal fun createKotlinCompilationArguments(): KotlinCompilationArgumentsImpl {
    return KotlinCompilationArgumentsImpl(
        defaultArguments = emptyArray(),
        currentArguments = emptyArray()
    )
}

internal fun createKotlinTaskProperties(): KotlinTaskPropertiesImpl {
    return KotlinTaskPropertiesImpl(
        null, null, null, null
    )
}

internal fun createKotlinTarget(
    name: String,
    platform: KotlinPlatform = KotlinPlatform.COMMON,
    compilations: Iterable<KotlinCompilation> = emptyList()
): KotlinTargetImpl {
    return KotlinTargetImpl(
        name = name,
        presetName = null,
        disambiguationClassifier = null,
        platform = platform,
        compilations = compilations.toList(),
        testRunTasks = emptyList(),
        nativeMainRunTasks = emptyList(),
        jar = null,
        konanArtifacts = emptyList()
    )
}