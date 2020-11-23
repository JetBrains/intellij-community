package org.jetbrains.kotlin.idea.configuration.mpp

import org.jetbrains.kotlin.idea.configuration.KotlinMPPGradleProjectResolver
import org.jetbrains.kotlin.idea.configuration.utils.fullName
import org.jetbrains.kotlin.idea.configuration.utils.getKotlinModuleId


internal fun KotlinMPPGradleProjectResolver.Companion.populateModuleDependenciesByCompilations(
    context: PopulateModuleDependenciesContext
): Unit = with(context) {
    getCompilations(gradleModule, mppModel, ideModule, resolverCtx)
        .filterNot { (_, compilation) -> delegateToAndroidPlugin(compilation) }
        .filter { (_, compilation) -> processedModuleIds.add(getKotlinModuleId(gradleModule, compilation, resolverCtx)) }
        .forEach { (dataNode, compilation) ->
            buildDependencies(
                resolverCtx, sourceSetMap, artifactsMap, dataNode, getDependencies(compilation), ideProject
            )
            for (sourceSet in compilation.sourceSets) {
                if (sourceSet.fullName() == compilation.fullName()) continue
                val targetDataNode = getSiblingKotlinModuleData(sourceSet, gradleModule, ideModule, resolverCtx) ?: continue
                addDependency(dataNode, targetDataNode, sourceSet.isTestModule)
            }
        }

}

