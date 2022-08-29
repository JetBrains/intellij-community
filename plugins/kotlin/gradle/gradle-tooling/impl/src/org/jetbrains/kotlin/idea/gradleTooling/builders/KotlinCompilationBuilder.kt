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

class KotlinCompilationBuilder(val platform: KotlinPlatform, val classifier: String?) :
    KotlinModelComponentBuilder<KotlinCompilationReflection, MultiplatformModelImportingContext, KotlinCompilation> {

    override fun buildComponent(
        origin: KotlinCompilationReflection,
        importingContext: MultiplatformModelImportingContext
    ): KotlinCompilationImpl? {
        val compilationName = origin.compilationName
        val kotlinGradleSourceSets = origin.sourceSets ?: return null
        val kotlinSourceSets = kotlinGradleSourceSets.mapNotNull { importingContext.sourceSetByName(it.name) }
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
