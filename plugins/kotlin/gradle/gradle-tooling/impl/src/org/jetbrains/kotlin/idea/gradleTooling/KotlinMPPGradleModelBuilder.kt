// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.jetbrains.kotlin.idea.gradleTooling.GradleImportProperties.*
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel.Companion.NO_KOTLIN_NATIVE_HOME
import org.jetbrains.kotlin.idea.gradleTooling.builders.KotlinSourceSetBuilder
import org.jetbrains.kotlin.idea.gradleTooling.builders.KotlinTargetBuilder
import org.jetbrains.kotlin.idea.gradleTooling.builders.buildIdeaKotlinDependenciesContainer
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinExtensionReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinMultiplatformImportReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinTargetReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet.Companion.COMMON_MAIN_SOURCE_SET_NAME
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet.Companion.COMMON_TEST_SOURCE_SET_NAME
import org.jetbrains.kotlin.idea.projectModel.KotlinTarget
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext


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
            val kotlinExtension = project.extensions.findByName("kotlin") ?: return null
            val kotlinExtensionReflection = KotlinExtensionReflection(project, kotlinExtension)
            if (!shouldBuild(kotlinExtensionReflection)) return null

            val importingContext = MultiplatformModelImportingContextImpl(
                project = project,
                importReflection = KotlinMultiplatformImportReflection(kotlinExtensionReflection),
                kotlinExtensionReflection = kotlinExtensionReflection,
                kotlinGradlePluginVersion = kotlinExtensionReflection.parseKotlinGradlePluginVersion(),
                modelBuilderContext = builderContext ?: return null
            )

            val sourceSets = buildSourceSets(importingContext)
            importingContext.initializeSourceSets(sourceSets)

            val targets = buildTargets(importingContext, kotlinExtensionReflection.targets)
            importingContext.initializeTargets(targets)
            importingContext.initializeCompilations(targets.flatMap { it.compilations })

            computeSourceSetsDeferredInfo(importingContext)

            val coroutinesState = getCoroutinesState(project)
            val kotlinNativeHome = KotlinNativeHomeEvaluator.getKotlinNativeHome(project) ?: NO_KOTLIN_NATIVE_HOME

            val dependenciesContainer = buildIdeaKotlinDependenciesContainer(importingContext, kotlinExtensionReflection)

            val model = KotlinMPPGradleModelImpl(
                sourceSetsByName = filterOrphanSourceSets(importingContext),
                targets = importingContext.targets,
                extraFeatures = ExtraFeaturesImpl(
                    coroutinesState = coroutinesState,
                    isHMPPEnabled = importingContext.isHMPPEnabled,
                ),
                kotlinNativeHome = kotlinNativeHome,
                dependencyMap = importingContext.dependencyMapper.toDependencyMap(),
                dependencies = dependenciesContainer,
                kotlinGradlePluginVersion = importingContext.kotlinGradlePluginVersion
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
            logger.warn("[sync warning] Source set \"${it.name}\" is not compiled with any compilation. This source set is not imported in the IDE.")
        }
        return nonOrphanSourceSets.associateBy { it.name }
    }

    private fun getCoroutinesState(project: Project): String? {
        val kotlinExt = project.extensions.findByName("kotlin") ?: return null
        val experimentalExt = kotlinExt["getExperimental"] ?: return null
        return experimentalExt["getCoroutines"] as? String
    }

    private fun buildSourceSets(
        importingContext: MultiplatformModelImportingContext
    ): List<KotlinSourceSetImpl> {
        val sourceSetBuilder = KotlinSourceSetBuilder(importingContext)
        return importingContext.kotlinExtensionReflection.sourceSets
            .mapNotNull { sourceSetReflection -> sourceSetBuilder.buildKotlinSourceSet(sourceSetReflection) }
    }

    private fun buildTargets(
        importingContext: MultiplatformModelImportingContext,
        projectTargets: List<KotlinTargetReflection>
    ): Collection<KotlinTarget> {
        return projectTargets.mapNotNull { kotlinTargetReflection ->
            KotlinTargetBuilder.buildComponent(kotlinTargetReflection, importingContext)
        }
    }

    private fun computeSourceSetsDeferredInfo(importingContext: MultiplatformModelImportingContext) {
        for (sourceSet in importingContext.sourceSets) {
            if (!importingContext.isHMPPEnabled) {
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

        if (!isHMPPEnabled && !isDeclaredSourceSet(sourceSet)) {
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

    private fun KotlinExtensionReflection.parseKotlinGradlePluginVersion(): KotlinGradlePluginVersion? {
        val version = KotlinGradlePluginVersion.parse(kotlinGradlePluginVersion ?: return null)
        if (version == null) {
            logger.warn("[sync warning] Failed to parse KotlinGradlePluginVersion: version == null")
        }
        return version
    }

    private fun shouldBuild(extension: KotlinExtensionReflection): Boolean {
        return extension.kotlinExtension.javaClass.getMethodOrNull("getTargets") != null && extension.targets.isNotEmpty()
    }

    companion object {
        private val DEFAULT_IMPORTING_CHECKERS = listOf(OrphanSourceSetImportingChecker)

        private fun KotlinMPPGradleModel.collectDiagnostics(importingContext: MultiplatformModelImportingContext): KotlinImportingDiagnosticsContainer =
            mutableSetOf<KotlinImportingDiagnostic>().apply {
                DEFAULT_IMPORTING_CHECKERS.forEach {
                    it.check(this@collectDiagnostics, this, importingContext)
                }
            }

        val logger = Logging.getLogger(KotlinMPPGradleModelBuilder::class.java)
    }
}
