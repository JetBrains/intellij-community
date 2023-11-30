// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.builders

import org.gradle.api.Task
import org.gradle.api.logging.Logging
import org.jetbrains.kotlin.idea.gradleTooling.*
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

        val dependencies = if (!importingContext.useKgpDependencyResolution()) buildCompilationDependencies(importingContext, origin)
        else emptySet()

        val kotlinTaskProperties = getKotlinTaskProperties(compileKotlinTask, classifier)

        val nativeExtensions = origin.konanTargetName?.let(::KotlinNativeCompilationExtensionsImpl)

        val allSourceSets = kotlinSourceSets
            .flatMap { sourceSet -> importingContext.resolveAllDependsOnSourceSets(sourceSet) }
            .union(kotlinSourceSets)

        val compilerArguments = resolveCompilerArguments(compileKotlinTask)
            ?.map(importingContext.interner::getOrPut)

        val associateCompilations = origin.associateCompilations.mapNotNull { associateCompilation ->
            KotlinCompilationCoordinatesImpl(
                targetName = associateCompilation.target?.targetName ?: return@mapNotNull null,
                compilationName = associateCompilation.compilationName
            )
        }

        val serializedExtras = importingContext.importReflection?.resolveExtrasSerialized(origin.gradleCompilation)

        val isTestCompilation = if (importingContext.getProperty(GradleImportProperties.LEGACY_TEST_SOURCE_SET_DETECTION)) {
            compilationName == KotlinCompilation.TEST_COMPILATION_NAME
                    || platform == KotlinPlatform.ANDROID && compilationName.contains("Test")
        } else {
            associateCompilations.isNotEmpty()
        }

        @Suppress("DEPRECATION_ERROR")
        return KotlinCompilationImpl(
            name = compilationName,
            allSourceSets = allSourceSets,
            declaredSourceSets = kotlinSourceSets,
            dependencies = dependencies.map { importingContext.dependencyMapper.getId(it) }.distinct().toTypedArray(),
            output = output,
            compilerArguments = compilerArguments,
            kotlinTaskProperties = kotlinTaskProperties,
            nativeExtensions = nativeExtensions,
            associateCompilations = associateCompilations.toSet(),
            extras = IdeaKotlinExtras.from(serializedExtras),
            isTestComponent = isTestCompilation,
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
            if (compilationReflection.target?.platformType != NATIVE_TARGET_PLATFORM_TYPE_NAME) return@apply

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
    }
}
