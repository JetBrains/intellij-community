// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinProjectArtifactDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinSourceDependency
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver

fun IdeaKotlinProjectArtifactDependency.Resolver.Companion.from(
    project: DataNode<ProjectData>
): IdeaKotlinProjectArtifactDependency.Resolver {
    val sourceSetMap = project.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS).orEmpty()
    val artifactsMap = project.getUserData(GradleProjectResolver.CONFIGURATION_ARTIFACTS).orEmpty()

    return byName { dependency ->
        val id = artifactsMap[dependency.coordinates.artifactFile.normalize().canonicalPath] ?: return@byName null
        val sourceSet: ExternalSourceSet = sourceSetMap[id]?.second ?: return@byName null
        sourceSet.name
    }
}

fun IdeaKotlinProjectArtifactDependency.Resolver.Companion.from(
    sourceSet: DataNode<*>
): IdeaKotlinProjectArtifactDependency.Resolver {
    return from(sourceSet.getParent(ProjectData::class.java) ?: return composite())
}

fun DataNode<*>.resolve(dependency: IdeaKotlinProjectArtifactDependency): IdeaKotlinSourceDependency? {
    return IdeaKotlinProjectArtifactDependency.Resolver.from(this).resolve(dependency)
}
