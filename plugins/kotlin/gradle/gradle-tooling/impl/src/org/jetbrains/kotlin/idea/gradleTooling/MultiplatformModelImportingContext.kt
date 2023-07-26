// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.Project
import org.jetbrains.kotlin.idea.gradleTooling.GradleImportProperties.ENABLE_KGP_DEPENDENCY_RESOLUTION
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinExtensionReflection
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinMultiplatformImportReflection
import org.jetbrains.kotlin.idea.projectModel.*
import org.jetbrains.kotlin.tooling.core.Interner
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
    val interner: Interner
    val kotlinGradlePluginVersion: KotlinGradlePluginVersion?

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

/**
 * HMPP is enabled by default since 1.6.x version, and since the 1.9.20 it is impossible to turn back the old mode.
 * In 1.9.20 we still set this property to true on KGP side so IDE's without this commit will behave correctly.
 *
 * If you are writing the new code -- you are free to assume that HMPP is enabled and do nothing for the old mode.
 *
 * We could remove this code completely from IDEA once we stop supporting the KGP with versions <= 1.9.0.
 * My bet is that in K2 plugin we could not support non-HMPP mode at all and let the code for K1 just rot.
 */
internal val MultiplatformModelImportingContext.isHMPPEnabled: Boolean
    get() =
        when (project.readProperty("kotlin.mpp.enableGranularSourceSetsMetadata")) {
            // KGP [1.6.0<=>1.9.20] set this property to true by default
            true -> true

            // it is possible to set this property to false only in KGP < 1.9.20, and we respect the disabling HMPP in such cases
            false -> false

            // since 2.0.0+ explicitValueIfAny should be null => HMPP is enabled
            null -> true
        }

internal fun Project.readProperty(propertyId: String): Boolean? =
    try {
        (findProperty(propertyId) as? String)?.toBoolean()
    } catch (e: Exception) {
        logger.error("Error while trying to read property $propertyId from project $project", e)
        null
    }

internal fun Project.getProperty(property: GradleImportProperties): Boolean =
    readProperty(property.id) ?: property.defaultValue

internal enum class GradleImportProperties(val id: String, val defaultValue: Boolean) {
    COERCE_ROOT_SOURCE_SETS_TO_COMMON("kotlin.mpp.coerceRootSourceSetsToCommon", true),
    IMPORT_ORPHAN_SOURCE_SETS("import_orphan_source_sets", true),
    ENABLE_KGP_DEPENDENCY_RESOLUTION("kotlin.mpp.import.enableKgpDependencyResolution", true),
    LEGACY_TEST_SOURCE_SET_DETECTION("kotlin.mpp.import.legacyTestSourceSetDetection", false),
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
    modelBuilderContext: ModelBuilderContext
) : MultiplatformModelImportingContext {

    override val interner: Interner = Interner()

    /** see [initializeSourceSets] */
    override lateinit var sourceSetsByName: Map<String, KotlinSourceSetImpl>
        private set

    private val downloadSources = java.lang.Boolean.parseBoolean(System.getProperty("idea.disable.gradle.download.sources", "true"))

    override val dependencyResolver = DependencyResolverImpl(project, false, downloadSources, SourceSetCachedFinder(modelBuilderContext))
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

    private fun initializeSourceSets(sourceSetsByNames: Map<String, KotlinSourceSetImpl>) {
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
