/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.builders

import org.gradle.api.Task
import org.gradle.api.logging.Logging
import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.reflect.*

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
        val dependencies = buildCompilationDependencies(importingContext, origin, classifier)
        val kotlinTaskProperties = getKotlinTaskProperties(compileKotlinTask, classifier)

        val nativeExtensions = origin.konanTargetName?.let(::KotlinNativeCompilationExtensionsImpl)

        val allSourceSets = kotlinSourceSets
            .flatMap { sourceSet -> importingContext.resolveAllDependsOnSourceSets(sourceSet) }
            .union(kotlinSourceSets)

        val compilerArguments = buildCompilationArguments(compileKotlinTask)

        @Suppress("DEPRECATION_ERROR")
        return KotlinCompilationImpl(
            name = compilationName,
            allSourceSets = allSourceSets,
            declaredSourceSets = if (platform == KotlinPlatform.ANDROID) allSourceSets else kotlinSourceSets,
            dependencies = dependencies.map { importingContext.dependencyMapper.getId(it) }.distinct().toTypedArray(),
            output = output,
            arguments = compilerArguments,
            dependencyClasspath = emptyArray(),
            kotlinTaskProperties = kotlinTaskProperties,
            nativeExtensions = nativeExtensions
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

        private fun buildCompilationDependencies(
            importingContext: MultiplatformModelImportingContext,
            compilationReflection: KotlinCompilationReflection,
            classifier: String?
        ): Set<KotlinDependency> {
            return LinkedHashSet<KotlinDependency>().apply {
                this += compileDependenciesBuilder.buildComponent(compilationReflection.gradleCompilation, importingContext)
                this += runtimeDependenciesBuilder.buildComponent(compilationReflection.gradleCompilation, importingContext)
                    .onlyNewDependencies(this)

                val sourceSet = importingContext.sourceSetByName(compilationFullName(compilationReflection.compilationName, classifier))
                this += sourceSet?.dependencies?.mapNotNull { importingContext.dependencyMapper.getDependency(it) } ?: emptySet()
            }
        }

        private fun buildCompilationArguments(compileKotlinTask: Task): KotlinCompilationArguments {
            val reflectionLogger = ReflectionLogger(logger)
            val currentArguments =
                compileKotlinTask.callReflectiveGetter<List<String>?>("getSerializedCompilerArguments", reflectionLogger).orEmpty()
            val defaultArguments =
                compileKotlinTask.callReflectiveGetter< List<String>?>("getDefaultSerializedCompilerArguments", reflectionLogger).orEmpty()
            return KotlinCompilationArgumentsImpl(defaultArguments.toTypedArray(), currentArguments.toTypedArray())
        }

        private fun buildCompilationOutput(
            kotlinCompilationOutputReflection: KotlinCompilationOutputReflection,
            compileKotlinTask: Task
        ): KotlinCompilationOutput? {
            val compilationOutputBase = KotlinCompilationOutputBuilder.buildComponent(kotlinCompilationOutputReflection) ?: return null
            val destinationDir = KotlinNativeCompileReflection(compileKotlinTask).destinationDir
            return KotlinCompilationOutputImpl(compilationOutputBase.classesDirs, destinationDir, compilationOutputBase.resourcesDir)
        }
    }
}