// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.KotlinModuleUtils.getKotlinModuleId
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

private fun KotlinMPPGradleProjectResolver.Companion.populateModuleDependenciesByPlatformPropagation(
    context: KotlinMppPopulateModuleDependenciesContext, sourceSet: KotlinSourceSet
) = with(context) {
    val sourceSetDataNode = getSiblingKotlinModuleData(sourceSet, gradleModule, ideModule, resolverCtx)?.cast<GradleSourceSetData>()
        ?: return

    val dependencies = mppModel.targets
        .flatMap { target -> target.compilations }
        .filter { compilation -> compilation.dependsOnSourceSet(mppModel, sourceSet) }
        .map { compilation -> compilation.dependencies.mapNotNull(mppModel.dependencyMap::get).toSet() }
        .reduceOrNull { acc, dependencies -> acc.intersect(dependencies) }.orEmpty()

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
