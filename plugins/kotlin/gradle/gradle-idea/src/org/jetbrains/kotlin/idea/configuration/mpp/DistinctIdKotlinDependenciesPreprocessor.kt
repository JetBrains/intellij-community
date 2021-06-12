package org.jetbrains.kotlin.idea.configuration.mpp

import org.jetbrains.kotlin.gradle.KotlinDependency

internal object DistinctIdKotlinDependenciesPreprocessor : KotlinDependenciesPreprocessor {
    override fun invoke(dependencies: Iterable<KotlinDependency>): List<KotlinDependency> {
        return dependencies
            .groupBy { dependency -> dependency.id }
            .mapValues { (_, dependenciesWithSameId) ->
                dependenciesWithSameId.firstOrNull { it.scope == "COMPILE" } ?: dependenciesWithSameId.lastOrNull()
            }
            .values
            .filterNotNull()
    }

}