// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.logging.Logging
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinLanguageSettingsReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinTargetReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinDependencyId
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.plugins.gradle.DefaultExternalDependencyId
import org.jetbrains.plugins.gradle.model.DefaultExternalLibraryDependency
import org.jetbrains.plugins.gradle.model.DefaultFileCollectionDependency
import java.io.File

class KotlinSourceSetProtoBuilder(
    val androidDeps: Map<String, List<Any>>?,
    val project: Project
) : KotlinMultiplatformComponentBuilderBase<KotlinSourceSetProto> {
    private val sourceSetsWithoutNeedOfBuildingDependenciesMetadata: Set<Named> by lazy {
        val isHMPPEnabled = project.getProperty(GradleImportProperties.IS_HMPP_ENABLED)
        if (!isHMPPEnabled) return@lazy emptySet()

        val sourceSetPlatforms = mutableMapOf<Named, MutableSet<KotlinPlatform>>()
        val targets = project.getTargets().orEmpty().map(::KotlinTargetReflection)

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

    override fun buildComponent(origin: Any, importingContext: MultiplatformModelImportingContext): KotlinSourceSetProto? {
        val gradleSourceSet = origin as Named

        val languageSettings = gradleSourceSet["getLanguageSettings"]
            ?.let { KotlinLanguageSettingsBuilder.buildComponent(KotlinLanguageSettingsReflection(it)) }
            ?: return null
        val sourceDirs = (gradleSourceSet["getKotlin"] as? SourceDirectorySet)?.srcDirs ?: emptySet()
        val resourceDirs = (gradleSourceSet["getResources"] as? SourceDirectorySet)?.srcDirs ?: emptySet()

        @Suppress("UNCHECKED_CAST")
        val dependsOnSourceSets = (gradleSourceSet["getDependsOn"] as? Set<Named>)
            ?.mapTo(LinkedHashSet()) { it.name }
            ?: emptySet<String>()

        val sourceSetDependenciesBuilder: () -> Array<KotlinDependencyId> = {
            val androidDependenciesForSourceSet = buildAndroidSourceSetDependencies(androidDeps, gradleSourceSet)
            val includeAndroidDependencies = importingContext.getProperty(GradleImportProperties.INCLUDE_ANDROID_DEPENDENCIES)

            val dependencies = when {
                !includeAndroidDependencies && gradleSourceSet in sourceSetsWithoutNeedOfBuildingDependenciesMetadata -> emptyList()
                else -> buildMetadataDependencies(gradleSourceSet, importingContext) + androidDependenciesForSourceSet
            }

            dependencies
                .map { importingContext.dependencyMapper.getId(it) }
                .distinct()
                .toTypedArray()
        }

        val intransitiveSourceSetDependenciesBuilder: () -> Array<KotlinDependencyId> = {
            buildIntransitiveSourceSetDependencies(gradleSourceSet, importingContext)
                .map { importingContext.dependencyMapper.getId(it) }
                .distinct()
                .toTypedArray()
        }

        return KotlinSourceSetProto(
            name = gradleSourceSet.name,
            languageSettings = languageSettings,
            sourceDirs = sourceDirs,
            resourceDirs = resourceDirs,
            regularDependencies = sourceSetDependenciesBuilder,
            intransitiveDependencies = intransitiveSourceSetDependenciesBuilder,
            dependsOnSourceSets = dependsOnSourceSets,
            additionalVisibleSourceSets = getAdditionalVisibleSourceSets(importingContext.project, gradleSourceSet)
        )
    }

    companion object {
        private val logger = Logging.getLogger(KotlinSourceSetProtoBuilder::class.java)

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
            override val configurationNameAccessor: String = "getIntransitiveMetadataConfigurationName"
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
