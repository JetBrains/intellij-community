// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.AbstractDependencyData
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinProjectArtifactDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinSourceDependency
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

fun DataNode<GradleSourceSetData>.addDependency(dependency: IdeaKotlinDependency): List<DataNode<out AbstractDependencyData<*>>> {
    return when (dependency) {
        is IdeaKotlinBinaryDependency -> listOfNotNull(addDependency(dependency))
        is IdeaKotlinSourceDependency -> listOfNotNull(addDependency(dependency))
        is IdeaKotlinProjectArtifactDependency -> addDependency(dependency)
    }
}

fun DataNode<GradleSourceSetData>.addDependency(dependency: IdeaKotlinProjectArtifactDependency): List<DataNode<out ModuleDependencyData>> {
    val context = ExternalSystemApiUtil.findParent(this, ProjectKeys.MODULE)?.kotlinMppGradleProjectResolverContext ?: return emptyList()
    return KotlinProjectArtifactDependencyResolver().resolve(context, this, dependency)
        .mapNotNull { sourceDependency -> addDependency(sourceDependency) }
}
