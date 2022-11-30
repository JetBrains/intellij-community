// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.KotlinModuleUtils.getKotlinModuleId
import org.jetbrains.kotlin.idea.gradleTooling.KotlinDependency
import org.jetbrains.kotlin.idea.gradleTooling.findCompilation
import org.jetbrains.kotlin.idea.gradleTooling.getCompilations
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform.*
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.plugins.gradle.model.ExternalProjectDependency
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

internal fun KotlinMPPGradleProjectResolver.Companion.populateModuleDependenciesByPlatformPropagation(
    context: KotlinMppPopulateModuleDependenciesContext
): Unit = with(context) {
    if (!mppModel.extraFeatures.isHMPPEnabled) return
    context.mppModel.sourceSetsByName.values
        .filter { sourceSet -> isDependencyPropagationAllowed(sourceSet) }
        .filter { sourceSet -> processedModuleIds.add(getKotlinModuleId(gradleIdeaModule, sourceSet, resolverCtx)) }
        .forEach { sourceSet -> populateModuleDependenciesByPlatformPropagation(context, sourceSet) }
}

//region implementation

/**
 * Used to mark a set of dependencies as 'associated with one compilation'
 */
private typealias CompilationDependencies = Set<KotlinDependency>

private fun KotlinMPPGradleProjectResolver.Companion.populateModuleDependenciesByPlatformPropagation(
    context: KotlinMppPopulateModuleDependenciesContext, sourceSet: KotlinSourceSet
) = with(context) {
    val sourceSetDataNode = getSiblingKotlinModuleData(sourceSet, gradleIdeaModule, ideModule, resolverCtx)?.cast<GradleSourceSetData>()
        ?: return

    val propagatedDependencies = findCompilationsToPropagateDependenciesFrom(sourceSet)
        .map { compilation -> resolveVisibleDependencies(compilation) }
        .dependencyIntersection()
        /*
         Dependency Propagation is also allowed for root or intermediate 'Android' source sets.
         However, this mechanism is relying on the mpp model to propagate dependencies, which
         is not capable of transforming aar dependencies.

         For now, only non-aar (jar) dependencies can be propagated.
         Android aar dependencies are not supported in source sets like 'commonMain',
         even when 'commonMain' is considered 'Android'
         */
        .filter { it.packaging != "aar" }

        /*
        For 'common' (more than one listed platform) source sets:
        Project dependencies will not be propagated, but will come from the listed 'source sets' dependencies instead.
        This is due to the fact that 'findCompilationsToPropagateDependenciesFrom' might not include the full list of
        compilations that the source set participates in.
        Therefore, the intersection of dependencies might contain to many visible source sets to other projects.

        The mechanism below is more correct in this case.
         */
        .filter { sourceSet.actualPlatforms.platforms.size == 1 || it !is ExternalProjectDependency }

    /*
    Dependency Propagation will not work for project <-> project dependencies.
    Source sets that shall receive propagated dependencies still report proper project <-> project dependencies.
    Note: This includes sourceSet dependencies as transformed by the 'DependencyAdjuster' where the source set
    is mentioned as the configuration of the project dependency!
    */
    val projectDependencies = sourceSet.additionalVisibleSourceSets.mapNotNull { mppModel.sourceSetsByName[it] }.plus(sourceSet)
        .flatMap { sourceSet -> getDependencies(sourceSet) }
        .filterIsInstance<ExternalProjectDependency>().toSet()

    val preprocessedDependencies = dependenciesPreprocessor(propagatedDependencies + projectDependencies)
    buildDependencies(resolverCtx, sourceSetMap, artifactsMap, sourceSetDataNode, preprocessedDependencies, ideProject)
}


private fun KotlinMppPopulateModuleDependenciesContext.isDependencyPropagationAllowed(sourceSet: KotlinSourceSet): Boolean {
    /*
    Source sets sharing code between JVM and Android are the only intermediate source sets that
    can effectively consume a dependency's platform artifact.
    When a library only offers a JVM variant, then Android and JVM consume this variant of the library.
    This will be replaced later on by [KT-43450](https://youtrack.jetbrains.com/issue/KT-43450)
     */
    if (sourceSet.actualPlatforms.platforms.toSet() == setOf(JVM, ANDROID)) {
        return true
    }

    /*
    Single jvm target | Single android target, intermediate source set use case.
    This source set shall also just propagate platform dependencies
    */
    if (mppModel.sourceSetsByName.values.any { otherSourceSet -> sourceSet.name in otherSourceSet.declaredDependsOnSourceSets } &&
        (sourceSet.actualPlatforms.platforms.singleOrNull() == JVM ||
                sourceSet.actualPlatforms.platforms.singleOrNull() == ANDROID)
    ) return true

    return false
}

private fun KotlinMppPopulateModuleDependenciesContext.findCompilationsToPropagateDependenciesFrom(
    sourceSet: KotlinSourceSet
): Set<KotlinCompilation> {
    return when (sourceSet.actualPlatforms.platforms.toSet()) {
        /*
        Sharing code between Android and JVM:
        In this case, we discriminate the Android compilation and only propagate jvm dependencies.
        This might lead to some libraries being propagated that are not available on Android, but at least gives
        some coverage.

        The dependency intersection cannot be built upon jvm + android, because the dependencies
        in this case will have *different ids*: As example:
        my-library-jvm (jvm) vs my-library-android-release (android)
         */
        setOf(JVM, ANDROID) -> mppModel.getCompilations(sourceSet).filter { compilation -> compilation.platform == JVM }
        else -> mppModel.getCompilations(sourceSet)
    }.toSet()
}


private fun KotlinMppPopulateModuleDependenciesContext.resolveVisibleDependencies(compilation: KotlinCompilation): CompilationDependencies {
    return compilation.associateCompilations.mapNotNull { coordinates -> mppModel.findCompilation(coordinates) }.plus(compilation)
        .flatMap { compilationOrAssociate -> compilationOrAssociate.dependencies.mapNotNull(mppModel.dependencyMap::get) }
        .toSet()
}

/**
 * Used to find out 'common' dependencies between compilations.
 * A dependency is considered 'common' if its dependency id is present in all sets of dependencies
 *
 * @return The intersection of all dependencies listed by their dependency ID
 */
private fun List<CompilationDependencies>.dependencyIntersection(): Set<KotlinDependency> {
    if (this.isEmpty()) return emptySet()
    if (this.size == 1) return first()

    val idIntersection = map { dependencies -> dependencies.map { it.id }.toSet() }
        .reduce { acc, ids -> acc intersect ids }

    return first().filter { dependency -> dependency.id in idIntersection }.toSet()
}

//endregion
