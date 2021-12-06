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
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilationOutput
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import java.io.File

class KotlinCompilationBuilder(val platform: KotlinPlatform, val classifier: String?) :
    KotlinMultiplatformComponentBuilder<KotlinCompilation> {

    override fun buildComponent(origin: Any, importingContext: MultiplatformModelImportingContext): KotlinCompilationImpl? {
        val gradleCompilation = origin as Named

        @Suppress("UNCHECKED_CAST")
        val kotlinGradleSourceSets = (gradleCompilation["getKotlinSourceSets"] as? Collection<Named>) ?: return null
        val kotlinSourceSets = kotlinGradleSourceSets.mapNotNull { importingContext.sourceSetByName(it.name) }
        val compileKotlinTask = gradleCompilation.getCompileKotlinTaskName(importingContext.project) ?: return null

        val output = buildCompilationOutput(gradleCompilation, compileKotlinTask) ?: return null
        val dependencies = buildCompilationDependencies(importingContext, gradleCompilation, classifier)
        val kotlinTaskProperties = getKotlinTaskProperties(compileKotlinTask, classifier)

        // Get konanTarget (for native compilations only).
        val konanTarget = gradleCompilation["getKonanTarget"]?.let { konanTarget ->
            konanTarget["getName"] as? String
        }

        val nativeExtensions = konanTarget?.let(::KotlinNativeCompilationExtensionsImpl)

        val allSourceSets = kotlinSourceSets
            .flatMap { sourceSet -> importingContext.resolveAllDependsOnSourceSets(sourceSet) }
            .union(kotlinSourceSets)

        val cachedArgsInfo = if (compileKotlinTask.isCompilerArgumentAware)
            buildCachedArgsInfo(compileKotlinTask, importingContext.compilerArgumentsCacheMapper)
        else
            buildSerializedArgsInfo(compileKotlinTask, importingContext.compilerArgumentsCacheMapper, logger)

        @Suppress("DEPRECATION_ERROR")
        return KotlinCompilationImpl(
            name = gradleCompilation.name,
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
            gradleCompilation: Named,
            compileKotlinTask: Task
        ): KotlinCompilationOutput? {
            val gradleOutput = gradleCompilation["getOutput"] ?: return null
            val compilationOutputBase = KotlinCompilationOutputBuilder.buildComponent(KotlinCompilationOutputReflection(gradleOutput)) ?: return null
            @Suppress("UNCHECKED_CAST") val destinationDir = compileKotlinTask["getDestinationDir"] as? File
            //TODO: Hack for KotlinNativeCompile
                ?: (compileKotlinTask["getOutputFile"] as? Property<File>)?.orNull?.parentFile
                ?: return null
            return KotlinCompilationOutputImpl(compilationOutputBase.classesDirs, destinationDir, compilationOutputBase.resourcesDir)
        }

        private val Task.isCompilerArgumentAware: Boolean
            get() = javaClass.classLoader.loadClassOrNull(COMPILER_ARGUMENT_AWARE_CLASS)?.isAssignableFrom(javaClass) ?: false

    }
}
