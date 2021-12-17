// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.gradle.api.Named
import org.gradle.api.Task
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.kotlin.idea.gradleTooling.arguments.buildCachedArgsInfo
import org.jetbrains.kotlin.idea.gradleTooling.arguments.buildSerializedArgsInfo
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinCompilationOutputReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinCompilationReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinNativeCompileReflection
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilationOutput
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import java.io.File

class KotlinCompilationBuilder(val platform: KotlinPlatform, val classifier: String?) :
    KotlinMultiplatformComponentBuilder<KotlinCompilation> {

    override fun buildComponent(origin: Any, importingContext: MultiplatformModelImportingContext): KotlinCompilationImpl? {
        val kotlinCompilationReflection = KotlinCompilationReflection(origin)
        origin as Named

        @Suppress("UNCHECKED_CAST")
        val kotlinGradleSourceSets = kotlinCompilationReflection.kotlinGradleSourceSets ?: return null
        val kotlinSourceSets = kotlinGradleSourceSets.mapNotNull { importingContext.sourceSetByName(it.name) }
        val compileKotlinTask = kotlinCompilationReflection.compileKotlinTaskName
            ?.let { importingContext.project.tasks.findByName(it) }
            ?: return null

        val output = kotlinCompilationReflection.compilationOutput?.let { buildCompilationOutput(it, compileKotlinTask) } ?: return null
        val dependencies = buildCompilationDependencies(importingContext, origin, classifier)
        val kotlinTaskProperties = getKotlinTaskProperties(compileKotlinTask, classifier)

        val nativeExtensions = kotlinCompilationReflection.konanTargetName?.let(::KotlinNativeCompilationExtensionsImpl)

        val allSourceSets = kotlinSourceSets
            .flatMap { sourceSet -> importingContext.resolveAllDependsOnSourceSets(sourceSet) }
            .union(kotlinSourceSets)

        val cachedArgsInfo = if (compileKotlinTask.isCompilerArgumentAware)
            buildCachedArgsInfo(compileKotlinTask, importingContext.compilerArgumentsCacheMapper)
        else
            buildSerializedArgsInfo(compileKotlinTask, importingContext.compilerArgumentsCacheMapper, logger)

        @Suppress("DEPRECATION_ERROR")
        return KotlinCompilationImpl(
            name = origin.name,
            allSourceSets = allSourceSets,
            declaredSourceSets = if (platform == KotlinPlatform.ANDROID) allSourceSets else kotlinSourceSets,
            dependencies = dependencies.map { importingContext.dependencyMapper.getId(it) }.distinct().toTypedArray(),
            output = output,
            arguments = KotlinCompilationArgumentsImpl(emptyArray(), emptyArray()),
            dependencyClasspath = emptyArray(),
            cachedArgsInfo = cachedArgsInfo,
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
            gradleCompilation: Named,
            classifier: String?
        ): Set<KotlinDependency> {
            return LinkedHashSet<KotlinDependency>().apply {
                this += compileDependenciesBuilder.buildComponent(gradleCompilation, importingContext)
                this += runtimeDependenciesBuilder.buildComponent(gradleCompilation, importingContext).onlyNewDependencies(this)

                val sourceSet = importingContext.sourceSetByName(compilationFullName(gradleCompilation.name, classifier))
                this += sourceSet?.dependencies?.mapNotNull { importingContext.dependencyMapper.getDependency(it) } ?: emptySet()
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

    }
}
