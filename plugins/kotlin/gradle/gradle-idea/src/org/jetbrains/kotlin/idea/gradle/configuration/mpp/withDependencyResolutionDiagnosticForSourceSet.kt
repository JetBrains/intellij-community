// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradle.configuration.mpp

import com.intellij.build.events.MessageEvent
import com.intellij.build.issue.BuildIssue
import org.jetbrains.plugins.gradle.issue.UnresolvedDependencySyncIssue
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

internal fun ProjectResolverContext.withDependencyResolutionDiagnosticForSourceSet(
    sourceSetData: GradleSourceSetData
): ProjectResolverContext {
    return SourceSetDependencyResolutionDiagnosticsProjectResolverContext(this, sourceSetData)
}

private class SourceSetDependencyResolutionDiagnosticsProjectResolverContext(
    private val ctx: ProjectResolverContext,
    private val sourceSetData: GradleSourceSetData,
) : ProjectResolverContext by ctx {

    override fun report(kind: MessageEvent.Kind, buildIssue: BuildIssue) {
        val modifiedBuildIssue = when (buildIssue) {
            is UnresolvedDependencySyncIssue -> buildIssue.copy(dependencyOwner = sourceSetData.id)
            else -> buildIssue
        }
        ctx.report(kind, modifiedBuildIssue)
    }
}