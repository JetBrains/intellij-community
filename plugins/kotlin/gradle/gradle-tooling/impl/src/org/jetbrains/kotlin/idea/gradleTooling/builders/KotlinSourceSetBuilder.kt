// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.kotlin.idea.gradleTooling.builders.KotlinAndroidSourceSetInfoBuilder.buildKotlinAndroidSourceSetInfo
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinSourceSetReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinDependencyId
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.tooling.core.withClosure
import org.jetbrains.plugins.gradle.DefaultExternalDependencyId
import org.jetbrains.plugins.gradle.model.DefaultExternalLibraryDependency
import org.jetbrains.plugins.gradle.model.DefaultFileCollectionDependency
import java.io.File

internal class KotlinSourceSetBuilder(
    private val context: MultiplatformModelImportingContext
) {
    private val sourceSetsWithoutNeedOfBuildingDependenciesMetadata: Set<Named> by lazy {
        val isHMPPEnabled = context.getProperty(GradleImportProperties.IS_HMPP_ENABLED)
        if (!isHMPPEnabled) return@lazy emptySet()

        val sourceSetPlatforms = mutableMapOf<Named, MutableSet<KotlinPlatform>>()
        val targets = context.kotlinExtensionReflection.targets

        for (target in targets) {
            val platform = target.platformType?.let { KotlinPlatform.byId(it) } ?: continue
            for (compilation in target.compilations.orEmpty()) {
                for (sourceSet in compilation.allSourceSets.orEmpty()) {
                    sourceSetPlatforms.getOrPut(sourceSet) { mutableSetOf() }.add(platform)
                }
            }
        }

        sourceSetPlatforms
            .filterValues { it.singleOrNull() == KotlinPlatform.ANDROID }
            .keys
    }

    private val androidDependencies: Map<String, List<Any>>? by lazy {
        if (context.getProperty(GradleImportProperties.INCLUDE_ANDROID_DEPENDENCIES)) {
            try {
                val resolverClass = context.kotlinExtensionReflection.kotlinExtension.javaClass
                    .classLoader.loadClass("org.jetbrains.kotlin.gradle.targets.android.internal.AndroidDependencyResolver")
                val getAndroidSourceSetDependencies = resolverClass.getMethodOrNull("getAndroidSourceSetDependencies", Project::class.java)
                val resolver = resolverClass.getField("INSTANCE").get(null)
                @Suppress("UNCHECKED_CAST")
                getAndroidSourceSetDependencies?.let { it(resolver, context.project) } as Map<String, List<Any>>?
            } catch (e: Exception) {
                KotlinMPPGradleModelBuilder.logger.info("Unexpected exception", e)
            }
        }
        null
    }

    fun buildKotlinSourceSet(sourceSetReflection: KotlinSourceSetReflection): KotlinSourceSetImpl? {
        val languageSettings = sourceSetReflection.languageSettings
            ?.let { KotlinLanguageSettingsBuilder.buildComponent(it) }
            ?: return null

        val sourceDirs = sourceSetReflection.kotlin?.srcDirs ?: emptySet()
        val resourceDirs = sourceSetReflection.resources?.srcDirs ?: emptySet()
        val dependsOnSourceSets = sourceSetReflection.dependsOn.map { it.name }.toSet()
        val additionalVisibleSourceSets = sourceSetReflection.additionalVisibleSourceSets.map { it.name }.toSet()

        val sourceSetDependencies: Array<KotlinDependencyId> = run dependencies@{
            /* Eagerly return empty, if dependencies are resolved using KGP */
            if (context.useKgpDependencyResolution()) return@dependencies emptyArray()

            val androidDependenciesForSourceSet = buildAndroidSourceSetDependencies(androidDependencies, sourceSetReflection.instance)
            val includeAndroidDependencies = context.getProperty(GradleImportProperties.INCLUDE_ANDROID_DEPENDENCIES)

            val dependencies = when {
                !includeAndroidDependencies && sourceSetReflection.instance in sourceSetsWithoutNeedOfBuildingDependenciesMetadata -> emptyList()
                else -> buildMetadataDependencies(sourceSetReflection.instance, context) + androidDependenciesForSourceSet
            }

            dependencies
                .map { context.dependencyMapper.getId(it) }
                .distinct()
                .toTypedArray()
        }

        val intransitiveSourceSetDependencies: Array<KotlinDependencyId> = run dependencies@{
            /* Eagerly return empty, if dependencies are resolved using KGP */
            if (context.useKgpDependencyResolution()) return@dependencies emptyArray()

            buildIntransitiveSourceSetDependencies(sourceSetReflection.instance, context)
                .map { context.dependencyMapper.getId(it) }
                .distinct()
                .toTypedArray()
        }

        val androidSourceSetInfo = if (context.kotlinGradlePluginVersion.supportsKotlinAndroidSourceSetInfo())
            sourceSetReflection.androidSourceSetInfo?.let { info -> buildKotlinAndroidSourceSetInfo(info) } else null

        val allDependsOnSourceSetNames = sourceSetReflection.dependsOn.map { it.name }.withClosure<String> { sourceSetName ->
            context.kotlinExtensionReflection.sourceSetsByName[sourceSetName]?.dependsOn.orEmpty().map { it.name }
        }

        return KotlinSourceSetImpl(
            name = sourceSetReflection.name,
            languageSettings = languageSettings,
            sourceDirs = sourceDirs,
            resourceDirs = resourceDirs,
            regularDependencies = sourceSetDependencies,
            intransitiveDependencies = intransitiveSourceSetDependencies,
            declaredDependsOnSourceSets = dependsOnSourceSets.toMutableSet(),
            allDependsOnSourceSets = allDependsOnSourceSetNames.toMutableSet(),
            additionalVisibleSourceSets = additionalVisibleSourceSets.toMutableSet(),
            androidSourceSetInfo = androidSourceSetInfo,
        )
    }

    companion object {
        private val logger = Logging.getLogger(KotlinSourceSetBuilder::class.java)

        private val apiMetadataDependenciesBuilder = object : KotlinMultiplatformDependenciesBuilder() {
            override val configurationNameAccessor: String = "getApiMetadataConfigurationName"
            override val scope: String = "COMPILE"
        }

        private val implementationMetadataDependenciesBuilder = object : KotlinMultiplatformDependenciesBuilder() {
            override val configurationNameAccessor: String = "getImplementationMetadataConfigurationName"
            override val scope: String = "COMPILE"
        }

        private val compileOnlyMetadataDependenciesBuilder = object : KotlinMultiplatformDependenciesBuilder() {
            override val configurationNameAccessor: String = "getCompileOnlyMetadataConfigurationName"
            override val scope: String = "COMPILE"
        }

        private val runtimeOnlyMetadataDependenciesBuilder = object : KotlinMultiplatformDependenciesBuilder() {
            override val configurationNameAccessor: String = "getRuntimeOnlyMetadataConfigurationName"
            override val scope: String = "RUNTIME"
        }

        private val intransitiveMetadataDependenciesBuilder = object : KotlinMultiplatformDependenciesBuilder() {
            override val configurationNameAccessor: String = INTRANSITIVE_METADATA_CONFIGURATION_NAME_ACCESSOR
            override val scope: String = "COMPILE"
        }

        private fun buildMetadataDependencies(
            gradleSourceSet: Named,
            importingContext: MultiplatformModelImportingContext
        ): List<KotlinDependency> {
            return ArrayList<KotlinDependency>().apply {
                this += apiMetadataDependenciesBuilder.buildComponent(gradleSourceSet, importingContext)
                this += implementationMetadataDependenciesBuilder.buildComponent(gradleSourceSet, importingContext)
                this += compileOnlyMetadataDependenciesBuilder.buildComponent(gradleSourceSet, importingContext)
                this += runtimeOnlyMetadataDependenciesBuilder.buildComponent(gradleSourceSet, importingContext).onlyNewDependencies(this)
            }
        }

        private fun buildAndroidSourceSetDependencies(
            androidDeps: Map<String, List<Any>>?,
            gradleSourceSet: Named
        ): Collection<KotlinDependency> {
            return androidDeps?.get(gradleSourceSet.name)?.mapNotNull { it ->
                @Suppress("UNCHECKED_CAST")
                val collection = it["getCollection"] as Set<File>?
                if (collection == null) {
                    DefaultExternalLibraryDependency().apply {
                        (id as? DefaultExternalDependencyId)?.apply {
                            name = it["getName"] as String?
                            group = it["getGroup"] as String?
                            version = it["getVersion"] as String?
                        }
                        file = it["getJar"] as File? ?: return@mapNotNull null.also {
                            logger.warn("[sync warning] ${gradleSourceSet.name}: $id does not resolve to a jar")
                        }
                        source = it["getSource"] as File?
                    }
                } else {
                    DefaultFileCollectionDependency(collection)
                }
            } ?: emptyList()
        }


        private fun buildIntransitiveSourceSetDependencies(
            gradleSourceSet: Named,
            importingContext: MultiplatformModelImportingContext
        ): List<KotlinDependency> =
            intransitiveMetadataDependenciesBuilder.buildComponent(gradleSourceSet, importingContext).toList()
    }
}


internal const val INTRANSITIVE_METADATA_CONFIGURATION_NAME_ACCESSOR = "getIntransitiveMetadataConfigurationName"
