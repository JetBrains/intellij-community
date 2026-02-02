// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.idea.gradleTooling.ExtraFeaturesImpl
import org.jetbrains.kotlin.idea.gradleTooling.IdeaKotlinDependenciesContainer
import org.jetbrains.kotlin.idea.gradleTooling.IdeaKotlinExtras
import org.jetbrains.kotlin.idea.gradleTooling.KotlinCompilationImpl
import org.jetbrains.kotlin.idea.gradleTooling.KotlinCompilationOutputImpl
import org.jetbrains.kotlin.idea.gradleTooling.KotlinDependency
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradlePluginVersion
import org.jetbrains.kotlin.idea.gradleTooling.KotlinLanguageSettingsImpl
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModelImpl
import org.jetbrains.kotlin.idea.gradleTooling.KotlinPlatformContainerImpl
import org.jetbrains.kotlin.idea.gradleTooling.KotlinSourceSetImpl
import org.jetbrains.kotlin.idea.gradleTooling.KotlinTargetImpl
import org.jetbrains.kotlin.idea.gradleTooling.KotlinTaskPropertiesImpl
import org.jetbrains.kotlin.idea.projectModel.ExtraFeatures
import org.jetbrains.kotlin.idea.projectModel.KotlinAndroidSourceSetInfo
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilationCoordinates
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilationOutput
import org.jetbrains.kotlin.idea.projectModel.KotlinDependencyId
import org.jetbrains.kotlin.idea.projectModel.KotlinNativeCompilationExtensions
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.kotlin.idea.projectModel.KotlinTarget
import org.jetbrains.kotlin.idea.projectModel.KotlinTaskProperties
import org.jetbrains.kotlin.idea.projectModel.KotlinWasmCompilationExtensions
import org.jetbrains.kotlin.tooling.core.MutableExtras
import org.jetbrains.kotlin.tooling.core.mutableExtrasOf

internal fun createKotlinMPPGradleModel(
    dependencies: IdeaKotlinDependenciesContainer? = null,
    dependencyMap: Map<KotlinDependencyId, KotlinDependency> = emptyMap(),
    sourceSets: Set<KotlinSourceSet> = emptySet(),
    targets: Iterable<KotlinTarget> = emptyList(),
    extraFeatures: ExtraFeatures = createExtraFeatures(),
    kotlinNativeHome: String = "",
    kotlinGradlePluginVersion: KotlinGradlePluginVersion? = null

): KotlinMPPGradleModelImpl {
    return KotlinMPPGradleModelImpl(
        dependencyMap = dependencyMap,
        dependencies = dependencies,
        sourceSetsByName = sourceSets.associateBy { it.name },
        targets = targets.toList(),
        extraFeatures = extraFeatures,
        kotlinNativeHome = kotlinNativeHome,
        kotlinGradlePluginVersion = kotlinGradlePluginVersion
    )
}

internal fun createExtraFeatures(
    coroutinesState: String? = null,
    isHmppEnabled: Boolean = false,
): ExtraFeaturesImpl {
    return ExtraFeaturesImpl(
        coroutinesState = coroutinesState,
        isHMPPEnabled = isHmppEnabled,
    )
}

internal fun createKotlinSourceSet(
    name: String,
    declaredDependsOnSourceSets: Set<String> = emptySet(),
    allDependsOnSourceSets: Set<String> = declaredDependsOnSourceSets,
    platforms: Set<KotlinPlatform> = emptySet(),
    androidSourceSetInfo: KotlinAndroidSourceSetInfo? = null,
    extras: MutableExtras = mutableExtrasOf()
): KotlinSourceSetImpl = KotlinSourceSetImpl(
    name = name,
    languageSettings = KotlinLanguageSettingsImpl(
        languageVersion = null,
        apiVersion = null,
        isProgressiveMode = false,
        enabledLanguageFeatures = emptySet(),
        optInAnnotationsInUse = emptySet(),
        compilerPluginArguments = emptyArray(),
        compilerPluginClasspath = emptySet(),
        freeCompilerArgs = emptyArray()
    ),
    sourceDirs = emptySet(),
    resourceDirs = emptySet(),
    regularDependencies = emptyArray(),
    intransitiveDependencies = emptyArray(),
    declaredDependsOnSourceSets = declaredDependsOnSourceSets,
    allDependsOnSourceSets = allDependsOnSourceSets,
    additionalVisibleSourceSets = emptySet(),
    actualPlatforms = KotlinPlatformContainerImpl().apply { pushPlatforms(platforms) },
    androidSourceSetInfo = androidSourceSetInfo,
    extras = IdeaKotlinExtras.wrap(extras)
)

internal fun createKotlinCompilation(
    name: String = "main",
    defaultSourceSets: Set<KotlinSourceSet> = emptySet(),
    allSourceSets: Set<KotlinSourceSet> = emptySet(),
    dependencies: Iterable<KotlinDependencyId> = emptyList(),
    output: KotlinCompilationOutput = createKotlinCompilationOutput(),
    compilerArguments: List<String> = emptyList(),
    kotlinTaskProperties: KotlinTaskProperties = createKotlinTaskProperties(),
    nativeExtensions: KotlinNativeCompilationExtensions? = null,
    wasmExtensions: KotlinWasmCompilationExtensions? = null,
    associateCompilations: Set<KotlinCompilationCoordinates> = emptySet(),
    extras: MutableExtras = mutableExtrasOf()
): KotlinCompilationImpl {
    return KotlinCompilationImpl(
        name = name,
        declaredSourceSets = defaultSourceSets,
        allSourceSets = allSourceSets,
        dependencies = dependencies.toList().toTypedArray(),
        output = output,
        compilerArguments = compilerArguments,
        kotlinTaskProperties = kotlinTaskProperties,
        nativeExtensions = nativeExtensions,
        wasmExtensions = wasmExtensions,
        associateCompilations = associateCompilations,
        extras = IdeaKotlinExtras.wrap(extras),
        isTestComponent = associateCompilations.isNotEmpty(),
        archiveFile = null,
    )
}

internal fun createKotlinCompilationOutput(): KotlinCompilationOutputImpl {
    return KotlinCompilationOutputImpl(
        classesDirs = emptySet(),
        effectiveClassesDir = null,
        resourcesDir = null
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
    compilations: Iterable<KotlinCompilation> = emptyList(),
    extras: MutableExtras = mutableExtrasOf()
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
        konanArtifacts = emptyList(),
        extras = IdeaKotlinExtras.wrap(extras)
    )
}
