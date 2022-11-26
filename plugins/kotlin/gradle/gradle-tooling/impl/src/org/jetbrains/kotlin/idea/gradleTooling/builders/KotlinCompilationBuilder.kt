// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.gradle.api.Task
import org.gradle.api.logging.Logging
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.kotlin.idea.gradleTooling.arguments.buildCachedArgsInfo
import org.jetbrains.kotlin.idea.gradleTooling.arguments.buildSerializedArgsInfo
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinCompilationOutputReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinCompilationReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinNativeCompileReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilationOutput
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.plugins.gradle.model.ExternalProjectDependency

class KotlinCompilationBuilder(val platform: KotlinPlatform, val classifier: String?) :
    KotlinModelComponentBuilder<KotlinCompilationReflection, MultiplatformModelImportingContext, KotlinCompilation> {

    override fun buildComponent(
        origin: KotlinCompilationReflection,
        importingContext: MultiplatformModelImportingContext
    ): KotlinCompilationImpl? {
        val compilationName = origin.compilationName
        val kotlinGradleSourceSets = origin.sourceSets ?: return null
        val kotlinSourceSets = kotlinGradleSourceSets.mapNotNull { importingContext.sourceSetByName(it.name) }.toMutableSet()
        val compileKotlinTask = origin.compileKotlinTaskName
            ?.let { importingContext.project.tasks.findByName(it) }
            ?: return null

        val output = origin.compilationOutput?.let { buildCompilationOutput(it, compileKotlinTask) } ?: return null
        val dependencies = buildCompilationDependencies(importingContext, origin)
        val kotlinTaskProperties = getKotlinTaskProperties(compileKotlinTask, classifier)

        val nativeExtensions = origin.konanTargetName?.let(::KotlinNativeCompilationExtensionsImpl)

        val allSourceSets = kotlinSourceSets
            .flatMap { sourceSet -> importingContext.resolveAllDependsOnSourceSets(sourceSet) }
            .union(kotlinSourceSets)

        val cachedArgsInfo = if (compileKotlinTask.isCompilerArgumentAware
            //TODO hotfix for KTIJ-21807.
            // Remove after proper implementation of org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile#setupCompilerArgs
            && !compileKotlinTask.isKotlinNativeCompileTask //TODO hotfix for KTIJ-21807. Replace after
        )
            buildCachedArgsInfo(compileKotlinTask, importingContext.compilerArgumentsCacheMapper)
        else
            buildSerializedArgsInfo(compileKotlinTask, importingContext.compilerArgumentsCacheMapper, logger)

        val associateCompilations = origin.associateCompilations.mapNotNull { associateCompilation ->
            KotlinCompilationCoordinatesImpl(
                targetName = associateCompilation.target?.targetName ?: return@mapNotNull null,
                compilationName = associateCompilation.compilationName
            )
        }

        @Suppress("DEPRECATION_ERROR")
        return KotlinCompilationImpl(
            name = compilationName,
            allSourceSets = allSourceSets,
            declaredSourceSets = if (platform == KotlinPlatform.ANDROID) allSourceSets else kotlinSourceSets,
            dependencies = dependencies.map { importingContext.dependencyMapper.getId(it) }.distinct().toTypedArray(),
            output = output,
            arguments = KotlinCompilationArgumentsImpl(emptyArray(), emptyArray()),
            dependencyClasspath = emptyArray(),
            cachedArgsInfo = cachedArgsInfo,
            kotlinTaskProperties = kotlinTaskProperties,
            nativeExtensions = nativeExtensions,
            associateCompilations = associateCompilations.toSet()
        )
    }

    companion object {
        private val logger = Logging.getLogger(KotlinCompilationBuilder::class.java)

        private val compileDependenciesBuilder = object : KotlinMultiplatformDependenciesBuilder() {
            override val configurationNameAccessor: String = "getCompileDependencyConfigurationName"
            override val scope: String = "COMPILE"
        }

        private val runtimeDependenciesBuilder = object : KotlinMultiplatformDependenciesBuilder() {
            override val configurationNameAccessor: String = "getRuntimeDependencyConfigurationName"
            override val scope: String = "RUNTIME"
        }

        private const val COMPILER_ARGUMENT_AWARE_CLASS = "org.jetbrains.kotlin.gradle.internal.CompilerArgumentAware"
        private const val KOTLIN_NATIVE_COMPILE_CLASS = "org.jetbrains.kotlin.gradle.tasks.AbstractKotlinNativeCompile"

        private fun buildCompilationDependencies(
            importingContext: MultiplatformModelImportingContext,
            compilationReflection: KotlinCompilationReflection,
        ): Set<KotlinDependency> {
            return LinkedHashSet<KotlinDependency>().apply {
                this += compileDependenciesBuilder.buildComponent(compilationReflection.gradleCompilation, importingContext)
                this += runtimeDependenciesBuilder.buildComponent(compilationReflection.gradleCompilation, importingContext)
                    .onlyNewDependencies(this)
                this += compilationDependenciesFromDeclaredSourceSets(importingContext, compilationReflection)
            }
        }

        /*
        * We have to add some source set dependencies into compilation dependencies to workaround K/N specifics.
        * We don't want to simply add all of them, because of KTIJ-20056 and potential other issues
        * caused by versions of the same dependency coming from compilation and its declared source sets independently.
        *
        * The first part of the additionally added dependencies are implicitly assumed (by the compiler)
        * K/N distribution dependencies that are not put into a native compilation classpath.
        * IDE can't rely on implicit assumption for building deps, so they are held in a dedicated source set configuration.
        *
        * The second part are project-to-project dependencies, which are not supported properly via affiliated
        * artifact mapping for native targets. Since native targets are not usable from non-MPP projects right now,
        * this has no other visible consequences, and so we can manually provide project dependencies from the source sets
        * without having a correct artifact mapping.
        *
        * Prior to KGP 1.5.20 intransitive metadata didn't exist, so we still add all source set dependencies for older KGP versions.
        */
        private fun compilationDependenciesFromDeclaredSourceSets(
            importingContext: MultiplatformModelImportingContext,
            compilationReflection: KotlinCompilationReflection,
        ): List<KotlinDependency> = ArrayList<KotlinDependency>().apply {
            val compilationSourceSets = compilationReflection.sourceSets ?: return@apply
            val isIntransitiveMetadataSupported = compilationSourceSets.all {
                it[INTRANSITIVE_METADATA_CONFIGURATION_NAME_ACCESSOR] != null
            }

            compilationSourceSets.mapNotNull { importingContext.sourceSetByName(it.name) }.forEach { compilationSourceSet ->
                if (isIntransitiveMetadataSupported) {
                    this += compilationSourceSet.intransitiveDependencies.mapNotNull {
                        importingContext.dependencyMapper.getDependency(it)
                    }
                    this += compilationSourceSet.regularDependencies.mapNotNull { dependencyId ->
                        importingContext.dependencyMapper.getDependency(dependencyId) as? ExternalProjectDependency
                    }
                } else {
                    this += compilationSourceSet.dependencies.mapNotNull {
                        importingContext.dependencyMapper.getDependency(it)
                    }
                }
            }
        }

        private fun buildCompilationOutput(
            kotlinCompilationOutputReflection: KotlinCompilationOutputReflection,
            compileKotlinTask: Task
        ): KotlinCompilationOutput? {
            val compilationOutputBase = KotlinCompilationOutputBuilder.buildComponent(kotlinCompilationOutputReflection) ?: return null
            val destinationDir = KotlinNativeCompileReflection(compileKotlinTask).destinationDir
            return KotlinCompilationOutputImpl(compilationOutputBase.classesDirs, destinationDir, compilationOutputBase.resourcesDir)
        }

        private val Task.isCompilerArgumentAware: Boolean
            get() = javaClass.classLoader.loadClassOrNull(COMPILER_ARGUMENT_AWARE_CLASS)?.isAssignableFrom(javaClass) ?: false
        private val Task.isKotlinNativeCompileTask: Boolean
            get() = javaClass.classLoader.loadClassOrNull(KOTLIN_NATIVE_COMPILE_CLASS)?.isAssignableFrom(javaClass) ?: false

    }
}
