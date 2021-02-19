// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.Logging
import org.jetbrains.kotlin.idea.gradleTooling.GradleImportProperties.*
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel.Companion.NO_KOTLIN_NATIVE_HOME
import org.jetbrains.kotlin.idea.gradleTooling.arguments.CACHE_MAPPER_BRANCHING
import org.jetbrains.kotlin.idea.gradleTooling.builders.KotlinSourceSetProtoBuilder
import org.jetbrains.kotlin.idea.gradleTooling.builders.KotlinTargetBuilder
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinTargetReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet.Companion.COMMON_MAIN_SOURCE_SET_NAME
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet.Companion.COMMON_TEST_SOURCE_SET_NAME
import org.jetbrains.kotlin.idea.projectModel.KotlinTarget
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext

private val MPP_BUILDER_LOGGER = Logging.getLogger(KotlinMPPGradleModelBuilder::class.java)

class KotlinMPPGradleModelBuilder : AbstractModelBuilderService() {
    override fun getErrorMessageBuilder(project: Project, e: Exception): ErrorMessageBuilder {
        return ErrorMessageBuilder
            .create(project, e, "Gradle import errors")
            .withDescription("Unable to build Kotlin project configuration")
    }

    override fun canBuild(modelName: String?): Boolean {
        return modelName == KotlinMPPGradleModel::class.java.name
    }

    override fun buildAll(modelName: String, project: Project, builderContext: ModelBuilderContext): KotlinMPPGradleModel? {
        return buildAll(project, builderContext)
    }

    private fun buildAll(project: Project, builderContext: ModelBuilderContext?): KotlinMPPGradleModel? {
        try {
            val projectTargets = project.getTargets() ?: return null
            val modelBuilderContext = builderContext ?: return null
            val masterCompilerArgumentsCacheMapper = modelBuilderContext.getData(CACHE_MAPPER_BRANCHING)
            val detachableCompilerArgumentsCacheMapper = masterCompilerArgumentsCacheMapper.branchOffDetachable()

            val importingContext =
                MultiplatformModelImportingContextImpl(project, detachableCompilerArgumentsCacheMapper, modelBuilderContext)

            importingContext.initializeSourceSets(buildSourceSets(importingContext) ?: return null)

            val targets = buildTargets(importingContext, projectTargets)
            importingContext.initializeTargets(targets)
            importingContext.initializeCompilations(targets.flatMap { it.compilations })

            computeSourceSetsDeferredInfo(importingContext)

            val coroutinesState = getCoroutinesState(project)
            val kotlinNativeHome = KotlinNativeHomeEvaluator.getKotlinNativeHome(project) ?: NO_KOTLIN_NATIVE_HOME
            val model = KotlinMPPGradleModelImpl(
                sourceSetsByName = filterOrphanSourceSets(importingContext),
                targets = importingContext.targets,
                extraFeatures = ExtraFeaturesImpl(
                    coroutinesState = coroutinesState,
                    isHMPPEnabled = importingContext.getProperty(IS_HMPP_ENABLED),
                    isNativeDependencyPropagationEnabled = importingContext.getProperty(ENABLE_NATIVE_DEPENDENCY_PROPAGATION)
                ),
                kotlinNativeHome = kotlinNativeHome,
                dependencyMap = importingContext.dependencyMapper.toDependencyMap(),
                partialCacheAware = detachableCompilerArgumentsCacheMapper.detachCacheAware()
            ).apply {
                kotlinImportingDiagnostics += collectDiagnostics(importingContext)
            }
            return model
        } catch (throwable: Throwable) {
            project.logger.error("Failed building KotlinMPPGradleModel", throwable)
            throw throwable
        }
    }

    private fun filterOrphanSourceSets(
        importingContext: MultiplatformModelImportingContext
    ): Map<String, KotlinSourceSetImpl> {
        if (importingContext.getProperty(IMPORT_ORPHAN_SOURCE_SETS)) return importingContext.sourceSetsByName

        val (orphanSourceSets, nonOrphanSourceSets) = importingContext.sourceSets.partition { importingContext.isOrphanSourceSet(it) }

        orphanSourceSets.forEach {
            MPP_BUILDER_LOGGER.warn("[sync warning] Source set \"${it.name}\" is not compiled with any compilation. This source set is not imported in the IDE.")
        }
        return nonOrphanSourceSets.associateBy { it.name }
    }

    private fun getCoroutinesState(project: Project): String? {
        val kotlinExt = project.extensions.findByName("kotlin") ?: return null
        val experimentalExt = kotlinExt["getExperimental"] ?: return null
        return experimentalExt["getCoroutines"] as? String
    }

    private fun buildSourceSets(importingContext: MultiplatformModelImportingContext): Map<String, KotlinSourceSetImpl>? {
        val kotlinExt = importingContext.project.extensions.findByName("kotlin") ?: return null
        val getSourceSets = kotlinExt.javaClass.getMethodOrNull("getSourceSets") ?: return null

        @Suppress("UNCHECKED_CAST")
        val sourceSets =
            (getSourceSets(kotlinExt) as? NamedDomainObjectContainer<Named>)?.asMap?.values ?: emptyList<Named>()
        val androidDeps = buildAndroidDeps(importingContext, kotlinExt.javaClass.classLoader)
        val sourceSetProtoBuilder = KotlinSourceSetProtoBuilder(androidDeps)

        val allSourceSetsProtosByNames = sourceSets.mapNotNull {
            sourceSetProtoBuilder.buildComponent(it, importingContext)
        }.associateBy { it.name }

        // Some performance optimisation: do not build metadata dependencies if source set is not common
        return if (importingContext.getProperty(BUILD_METADATA_DEPENDENCIES)) {
            allSourceSetsProtosByNames.mapValues { (_, proto) ->
                proto.buildKotlinSourceSetImpl(true, allSourceSetsProtosByNames)
            }
        } else {
            val unactualizedSourceSets = allSourceSetsProtosByNames.values.flatMap { it.dependsOnSourceSets }.distinct()
            allSourceSetsProtosByNames.mapValues { (name, proto) ->
                proto.buildKotlinSourceSetImpl(unactualizedSourceSets.contains(name), allSourceSetsProtosByNames)
            }
        }
    }

    private fun buildAndroidDeps(importingContext: MultiplatformModelImportingContext, classLoader: ClassLoader): Map<String, List<Any>>? {
        if (importingContext.getProperty(INCLUDE_ANDROID_DEPENDENCIES)) {
            try {
                val resolverClass = classLoader.loadClass("org.jetbrains.kotlin.gradle.targets.android.internal.AndroidDependencyResolver")
                val getAndroidSourceSetDependencies = resolverClass.getMethodOrNull("getAndroidSourceSetDependencies", Project::class.java)
                val resolver = resolverClass.getField("INSTANCE").get(null)
                @Suppress("UNCHECKED_CAST")
                return getAndroidSourceSetDependencies?.let { it(resolver, importingContext.project) } as Map<String, List<Any>>?
            } catch (e: Exception) {
                MPP_BUILDER_LOGGER.info("Unexpected exception", e)
            }
        }
        return null
    }

    private fun buildTargets(
        importingContext: MultiplatformModelImportingContext,
        projectTargets: Collection<Named>
    ): Collection<KotlinTarget> {
        return projectTargets.mapNotNull {
            KotlinTargetBuilder.buildComponent(KotlinTargetReflection(it), importingContext)
        }
    }

    private fun computeSourceSetsDeferredInfo(importingContext: MultiplatformModelImportingContext) {
        for (sourceSet in importingContext.sourceSets) {
            if (!importingContext.getProperty(IS_HMPP_ENABLED)) {
                val name = sourceSet.name
                if (name == COMMON_MAIN_SOURCE_SET_NAME) {
                    sourceSet.isTestComponent = false
                    continue
                }
                if (name == COMMON_TEST_SOURCE_SET_NAME) {
                    sourceSet.isTestComponent = true
                    continue
                }
            }

            sourceSet.isTestComponent = importingContext.compilationsBySourceSet(sourceSet)?.all { it.isTestComponent } ?: false

            importingContext.computeSourceSetPlatforms(sourceSet)
        }
    }

    private fun MultiplatformModelImportingContext.computeSourceSetPlatforms(sourceSet: KotlinSourceSetImpl) {
        require(!sourceSet.actualPlatforms.arePlatformsInitialized) {
            "Attempt to re-initialize platforms for source set ${sourceSet}. Already present platforms: ${sourceSet.actualPlatforms}"
        }

        if (isOrphanSourceSet(sourceSet)) {
            // Explicitly set platform of orphan source-sets to only used platforms, not all supported platforms
            // Otherwise, the tooling might be upset after trying to provide some support for a target which actually
            // doesn't exist in this project (e.g. after trying to draw gutters, while test tasks do not exist)
            sourceSet.actualPlatforms.pushPlatforms(projectPlatforms)
            return
        }

        if (shouldCoerceToCommon(sourceSet)) {
            sourceSet.actualPlatforms.pushPlatforms(KotlinPlatform.COMMON)
            return
        }

        if (!getProperty(IS_HMPP_ENABLED) && !isDeclaredSourceSet(sourceSet)) {
            // intermediate source sets should be common if HMPP is disabled
            sourceSet.actualPlatforms.pushPlatforms(KotlinPlatform.COMMON)
            return
        }

        compilationsBySourceSet(sourceSet)?.let { compilations ->
            val platforms = compilations.map { it.platform }
            sourceSet.actualPlatforms.pushPlatforms(platforms)
        }
    }

    private fun MultiplatformModelImportingContext.shouldCoerceToCommon(sourceSet: KotlinSourceSetImpl): Boolean {
        val isHMPPEnabled = getProperty(IS_HMPP_ENABLED)
        val coerceRootSourceSetsToCommon = getProperty(COERCE_ROOT_SOURCE_SETS_TO_COMMON)
        val isRoot = sourceSet.name == COMMON_MAIN_SOURCE_SET_NAME || sourceSet.name == COMMON_TEST_SOURCE_SET_NAME

        // never makes sense to coerce single-targeted source-sets
        if (sourceSet.actualPlatforms.platforms.size == 1) return false

        return when {
            // pre-HMPP has only single-targeted source sets and COMMON
            !isHMPPEnabled -> true

            // in HMPP, we might want to coerce source sets to common, but only root ones, and only
            // when the corresponding setting is turned on
            isHMPPEnabled && isRoot && coerceRootSourceSetsToCommon -> true

            // in all other cases, in HMPP we shouldn't coerce anything
            else -> false
        }
    }

    companion object {
        private val DEFAULT_IMPORTING_CHECKERS = listOf(OrphanSourceSetImportingChecker)

        private fun KotlinMPPGradleModel.collectDiagnostics(importingContext: MultiplatformModelImportingContext): KotlinImportingDiagnosticsContainer =
            mutableSetOf<KotlinImportingDiagnostic>().apply {
                DEFAULT_IMPORTING_CHECKERS.forEach {
                    it.check(this@collectDiagnostics, this, importingContext)
                }
            }
    }
}