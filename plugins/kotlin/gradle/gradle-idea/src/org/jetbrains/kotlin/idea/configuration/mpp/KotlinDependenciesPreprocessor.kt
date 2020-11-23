package org.jetbrains.kotlin.idea.configuration.mpp

import org.jetbrains.kotlin.gradle.KotlinDependency

internal interface KotlinDependenciesPreprocessor {
    operator fun invoke(dependencies: Iterable<KotlinDependency>): List<KotlinDependency>
}

internal operator fun KotlinDependenciesPreprocessor.plus(
    other: KotlinDependenciesPreprocessor
): KotlinDependenciesPreprocessor {
    return SequentialKotlinDependenciesPreprocessor(this, other)
}

private class SequentialKotlinDependenciesPreprocessor(
    private val first: KotlinDependenciesPreprocessor,
    private val second: KotlinDependenciesPreprocessor
) : KotlinDependenciesPreprocessor {
    override fun invoke(dependencies: Iterable<KotlinDependency>): List<KotlinDependency> {
        return second(first(dependencies))
    }
}