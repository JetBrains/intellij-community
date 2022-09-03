// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.KotlinModuleUtils.getKotlinModuleId
import org.jetbrains.kotlin.idea.gradleTooling.KotlinDependency
import org.jetbrains.kotlin.idea.gradleTooling.findCompilation
import org.jetbrains.kotlin.idea.gradleTooling.getCompilations
import org.jetbrains.kotlin.idea.projectModel.KotlinCompilation
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

internal fun KotlinMPPGradleProjectResolver.Companion.populateModuleDependenciesByPlatformPropagation(
    context: KotlinMppPopulateModuleDependenciesContext
): Unit = with(context) {
    if (!mppModel.extraFeatures.isHMPPEnabled) return
    context.mppModel.sourceSetsByName.values
        .filter { sourceSet -> isDependencyPropagationAllowed(sourceSet) }
        .filter { sourceSet -> processedModuleIds.add(getKotlinModuleId(gradleModule, sourceSet, resolverCtx)) }
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
    val sourceSetDataNode = getSiblingKotlinModuleData(sourceSet, gradleModule, ideModule, resolverCtx)?.cast<GradleSourceSetData>()
        ?: return

    val dependencies = mppModel.getCompilations(sourceSet)
        .map { compilation -> resolveVisibleDependencies(compilation) }
        .dependencyIntersection()

    val preprocessedDependencies = dependenciesPreprocessor(dependencies)
    buildDependencies(resolverCtx, sourceSetMap, artifactsMap, sourceSetDataNode, preprocessedDependencies, ideProject)
}


private fun KotlinMppPopulateModuleDependenciesContext.isDependencyPropagationAllowed(sourceSet: KotlinSourceSet): Boolean {
    /*
    Source sets sharing code between JVM and Android are the only intermediate source sets that
    can effectively consume a dependency's platform artifact.
    When a library only offers a JVM variant, then Android and JVM consume this variant of the library.
    This will be replaced later on by [KT-43450](https://youtrack.jetbrains.com/issue/KT-43450)
     */
    if (sourceSet.actualPlatforms.platforms.toSet() == setOf(KotlinPlatform.JVM, KotlinPlatform.ANDROID)) {
        return true
    }

    /*
    Single jvm target, intermediate source set use case.
    This source set shall also just propagate platform dependencies
     */
    if (sourceSetVisibilityGraph.successors(sourceSet).isNotEmpty() &&
        sourceSet.actualPlatforms.platforms.singleOrNull() == KotlinPlatform.JVM
    ) return true

    return false
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
private fun List<CompilationDependencies>.dependencyIntersection(): List<KotlinDependency> {
    if (this.isEmpty()) return emptyList()

    val idIntersection = map { dependencies -> dependencies.map { it.id }.toSet() }
        .reduce { acc, ids -> acc intersect ids }

    return first().filter { dependency -> dependency.id in idIntersection }
}

//endregion
