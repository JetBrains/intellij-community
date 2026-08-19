// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface KaptGradleDependenciesManipulator {
    fun addDependencies(dependencies: List<KaptProcessorDependency>)
    fun removeDependencies(dependencies: List<KaptProcessorDependency>)
    fun reformat()
}

@ApiStatus.Internal
data class KaptProcessorDependency(
    val match: MatchResult,
    val dependencyConfiguration: String,
    val kaptConfiguration: String,
    val notation: String,
    val dropOriginal: Boolean,
)
