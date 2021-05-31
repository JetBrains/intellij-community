// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.configuration.mpp

import org.jetbrains.kotlin.gradle.KotlinDependency

interface KotlinDependenciesPreprocessor {
    operator fun invoke(dependencies: Iterable<KotlinDependency>): List<KotlinDependency>
}

operator fun KotlinDependenciesPreprocessor.plus(
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