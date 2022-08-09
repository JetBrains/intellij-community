// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradle.configuration.mpp

import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.kotlin.idea.gradleTooling.KotlinDependency

@IntellijInternalApi
object DistinctIdKotlinDependenciesPreprocessor : KotlinDependenciesPreprocessor {
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