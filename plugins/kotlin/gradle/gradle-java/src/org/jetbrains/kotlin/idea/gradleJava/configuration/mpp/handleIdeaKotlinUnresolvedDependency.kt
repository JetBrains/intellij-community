// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinUnresolvedBinaryDependency
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMppGradleProjectResolver
import org.jetbrains.plugins.gradle.issue.UnresolvedDependencySyncIssue

internal fun reportIdeaKotlinUnresolvedDependency(
    dependency: IdeaKotlinUnresolvedBinaryDependency,
    context: KotlinMppGradleProjectResolver.Context,
    sourceSetModuleId: KotlinSourceSetModuleId,
) {
    context.resolverCtx.reporter.issue(
        UnresolvedDependencySyncIssue(
            dependencyName = dependency.coordinates?.toString() ?: "<unknown coordinates>",
            failureMessage = dependency.cause,
            projectPath = context.resolverCtx.projectPath,
            isOfflineMode = context.resolverCtx.settings.isOfflineWork,
            dependencyOwner = sourceSetModuleId.toString(),
        )
    )
}
