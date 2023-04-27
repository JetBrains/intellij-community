// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import org.jetbrains.kotlin.idea.gradle.configuration.klib.KotlinNativeLibraryNameUtil
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMppGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.KotlinModuleUtils.getKotlinModuleId
import org.jetbrains.kotlin.idea.gradleTooling.KotlinDependency
import org.jetbrains.kotlin.idea.gradleTooling.resolveAllDependsOnSourceSets
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

internal fun KotlinMppGradleProjectResolver.Companion.populateModuleDependenciesBySourceSetVisibilityGraph(
    context: KotlinMppPopulateModuleDependenciesContext
): Unit = with(context) {
    for (sourceSet in mppModel.sourceSetsByName.values) {
        if (shouldDelegateToOtherPlugin(sourceSet)) continue

        val dependsOnSourceSets = mppModel.resolveAllDependsOnSourceSets(sourceSet)
        val additionalVisibleSourceSets = sourceSet.additionalVisibleSourceSets.mapNotNull(mppModel.sourceSetsByName::get)

        val visibleSourceSets = dependsOnSourceSets + additionalVisibleSourceSets - sourceSet

        val fromDataNode = getSiblingKotlinModuleData(sourceSet, gradleIdeaModule, ideModule, resolverCtx)?.cast<GradleSourceSetData>()
            ?: continue

        /* Add dependencies from current sourceSet to all visible source sets (dependsOn, test to production, ...)*/
        for (visibleSourceSet in visibleSourceSets) {
            val toDataNode = getSiblingKotlinModuleData(visibleSourceSet, gradleIdeaModule, ideModule, resolverCtx) ?: continue
            addDependency(fromDataNode, toDataNode, visibleSourceSet.isTestComponent)
        }

        if (!processedModuleIds.add(getKotlinModuleId(gradleIdeaModule, sourceSet, resolverCtx))) continue
        val directDependencies = getDependencies(sourceSet).toSet()
        val directIntransitiveDependencies = getIntransitiveDependencies(sourceSet).toSet()
        val dependenciesFromVisibleSourceSets = getDependenciesFromVisibleSourceSets(visibleSourceSets)

        val dependencies = dependenciesPreprocessor(
            dependenciesFromVisibleSourceSets + directDependencies + directIntransitiveDependencies
        )

        buildDependencies(resolverCtx, sourceSetMap, artifactsMap, fromDataNode, dependencies, ideProject)
    }
}


private fun KotlinMppPopulateModuleDependenciesContext.getDependenciesFromVisibleSourceSets(
    visibleSourceSets: Set<KotlinSourceSet>
): Set<KotlinDependency> {
    return visibleSourceSets
        .flatMap { visibleSourceSet -> getRegularDependencies(visibleSourceSet) }
        .filter { !it.name.startsWith(KotlinNativeLibraryNameUtil.KOTLIN_NATIVE_LIBRARY_PREFIX_PLUS_SPACE) }
        .toSet()
}
