// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.Project
import org.jetbrains.kotlin.idea.gradleTooling.GradleImportProperties.ENABLE_KGP_DEPENDENCY_RESOLUTION
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinExtensionReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinMultiplatformImportReflection
import org.jetbrains.kotlin.idea.projectModel.*
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.util.DependencyResolver
import org.jetbrains.plugins.gradle.tooling.util.SourceSetCachedFinder
import org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl

interface HasDependencyResolver {
    val dependencyResolver: DependencyResolver
    val dependencyMapper: KotlinDependencyMapper
}

interface MultiplatformModelImportingContext : KotlinSourceSetContainer, HasDependencyResolver {
    val project: Project
    val kotlinGradlePluginVersion: KotlinGradlePluginVersion?
    val compilerArgumentsCacheMapper: CompilerArgumentsCacheMapper

    val importReflection: KotlinMultiplatformImportReflection?
    val kotlinExtensionReflection: KotlinExtensionReflection

    val targets: Collection<KotlinTarget>
    val compilations: Collection<KotlinCompilation>

    /**
     * All source sets in a project, including those that are created but not included into any compilations
     * (so-called "orphan" source sets). Use [isOrphanSourceSet] to get only compiled source sets
     */
    val sourceSets: Collection<KotlinSourceSetImpl> get() = sourceSetsByName.values
    override val sourceSetsByName: Map<String, KotlinSourceSetImpl>

    /**
     * Platforms, which are actually used in this project (i.e. platforms, for which targets has been created)
     */
    val projectPlatforms: Collection<KotlinPlatform>

    fun sourceSetByName(name: String): KotlinSourceSet?
    fun compilationsBySourceSet(sourceSet: KotlinSourceSet): Collection<KotlinCompilation>?

    /**
     * "Orphan" is a source set which is not actually compiled by the compiler, i.e. the one
     * which doesn't belong to any [KotlinCompilation].
     *
     * Orphan source sets might appear if one creates a source-set manually and doesn't link
     * it anywhere (essentially this is a misconfiguration)
     */
    fun isOrphanSourceSet(sourceSet: KotlinSourceSet): Boolean = compilationsBySourceSet(sourceSet) == null

    /**
     * "Declared" source-set is a source-set which is included into compilation directly, rather
     * through closure over dependsOn-relation.
     *
     * See also KDoc for [KotlinCompilation.declaredSourceSets]
     */
    fun isDeclaredSourceSet(sourceSet: KotlinSourceSet): Boolean
}

internal fun MultiplatformModelImportingContext.getProperty(property: GradleImportProperties): Boolean = project.getProperty(property)

internal fun Project.getProperty(property: GradleImportProperties): Boolean {
    val explicitValueIfAny = try {
        (findProperty(property.id) as? String)?.toBoolean()
    } catch (e: Exception) {
        logger.error("Error while trying to read property $property from project $project", e)
        null
    }

    return explicitValueIfAny ?: property.defaultValue
}

internal enum class GradleImportProperties(val id: String, val defaultValue: Boolean) {
    IS_HMPP_ENABLED("kotlin.mpp.enableGranularSourceSetsMetadata", false),
    COERCE_ROOT_SOURCE_SETS_TO_COMMON("kotlin.mpp.coerceRootSourceSetsToCommon", true),
    IMPORT_ORPHAN_SOURCE_SETS("import_orphan_source_sets", true),
    INCLUDE_ANDROID_DEPENDENCIES("kotlin.include.android.dependencies", false),
    ENABLE_KGP_DEPENDENCY_RESOLUTION("kotlin.mpp.import.enableKgpDependencyResolution", true)
    ;
}

internal fun MultiplatformModelImportingContext.useKgpDependencyResolution(): Boolean {
    return this.importReflection != null && getProperty(ENABLE_KGP_DEPENDENCY_RESOLUTION)
}

internal class MultiplatformModelImportingContextImpl(
    override val project: Project,
    override val importReflection: KotlinMultiplatformImportReflection?,
    override val kotlinExtensionReflection: KotlinExtensionReflection,
    override val kotlinGradlePluginVersion: KotlinGradlePluginVersion?,
    override val compilerArgumentsCacheMapper: CompilerArgumentsCacheMapper,
    modelBuilderContext: ModelBuilderContext
) : MultiplatformModelImportingContext {


    /** see [initializeSourceSets] */
    override lateinit var sourceSetsByName: Map<String, KotlinSourceSetImpl>
        private set

    override val dependencyResolver = DependencyResolverImpl(project, false, true, SourceSetCachedFinder(modelBuilderContext))
    override val dependencyMapper = KotlinDependencyMapper()

    /** see [initializeCompilations] */
    override lateinit var compilations: Collection<KotlinCompilation>
        private set
    private lateinit var sourceSetToParticipatedCompilations: Map<KotlinSourceSet, Set<KotlinCompilation>>
    private lateinit var allDeclaredSourceSets: Set<KotlinSourceSet>


    /** see [initializeTargets] */
    override lateinit var targets: Collection<KotlinTarget>
        private set

    override lateinit var projectPlatforms: Collection<KotlinPlatform>
        private set

    internal fun initializeSourceSets(sourceSetsByNames: Map<String, KotlinSourceSetImpl>) {
        require(!this::sourceSetsByName.isInitialized) {
            "Attempt to re-initialize source sets for $this. Previous value: ${this.sourceSetsByName}"
        }
        this.sourceSetsByName = sourceSetsByNames
    }

    internal fun initializeSourceSets(sourceSets: List<KotlinSourceSetImpl>) {
        initializeSourceSets(sourceSets.associateBy { it.name })
    }

    internal fun initializeCompilations(compilations: Collection<KotlinCompilation>) {
        require(!this::compilations.isInitialized) { "Attempt to re-initialize compilations for $this. Previous value: ${this.compilations}" }
        this.compilations = compilations

        val sourceSetToCompilations = LinkedHashMap<KotlinSourceSet, MutableSet<KotlinCompilation>>()

        for (target in targets) {
            for (compilation in target.compilations) {
                for (sourceSet in compilation.allSourceSets) {
                    sourceSetToCompilations.getOrPut(sourceSet) { LinkedHashSet() } += compilation
                    resolveAllDependsOnSourceSets(sourceSet).forEach {
                        sourceSetToCompilations.getOrPut(it) { LinkedHashSet() } += compilation
                    }
                }
            }
        }

        this.sourceSetToParticipatedCompilations = sourceSetToCompilations

        this.allDeclaredSourceSets = compilations.flatMapTo(mutableSetOf()) { it.declaredSourceSets }
    }

    internal fun initializeTargets(targets: Collection<KotlinTarget>) {
        require(!this::targets.isInitialized) { "Attempt to re-initialize targets for $this. Previous value: ${this.targets}" }
        this.targets = targets
        this.projectPlatforms = targets.map { it.platform }
    }

    // overload for small optimization
    override fun isOrphanSourceSet(sourceSet: KotlinSourceSet): Boolean = sourceSet !in sourceSetToParticipatedCompilations.keys

    override fun isDeclaredSourceSet(sourceSet: KotlinSourceSet): Boolean = sourceSet in allDeclaredSourceSets

    override fun compilationsBySourceSet(sourceSet: KotlinSourceSet): Collection<KotlinCompilation>? =
        sourceSetToParticipatedCompilations[sourceSet]

    override fun sourceSetByName(name: String): KotlinSourceSet? = sourceSetsByName[name]
}
