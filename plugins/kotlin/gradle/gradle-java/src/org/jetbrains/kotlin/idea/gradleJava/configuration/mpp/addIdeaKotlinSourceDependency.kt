// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.roots.DependencyScope
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinSourceDependency
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinSourceSetData
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

fun DataNode<GradleSourceSetData>.addDependency(dependency: IdeaKotlinSourceDependency): DataNode<out ModuleDependencyData>? {
    val dependencyNode = findModuleDependencyNode(dependency.kotlinSourceSetModuleId) ?: run create@{
        /* Create module dependency */
        val projectNode = ExternalSystemApiUtil.findParent(this, ProjectKeys.PROJECT) ?: return null
        val dependencyNode = projectNode.findSourceSetDataNode(dependency.kotlinSourceSetModuleId) ?: return null

        val moduleDependencyData = ModuleDependencyData(this.data, dependencyNode.data)
        moduleDependencyData.scope = DependencyScope.COMPILE
        createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData)
    }

    kotlinSourceSetData?.sourceSetInfo?.let { kotlinSourceSetInfo ->
        when (dependency.type) {
            IdeaKotlinSourceDependency.Type.Regular -> Unit
            IdeaKotlinSourceDependency.Type.Friend -> kotlinSourceSetInfo.additionalVisible += dependencyNode.data.target.id
            IdeaKotlinSourceDependency.Type.DependsOn -> kotlinSourceSetInfo.dependsOn += dependencyNode.data.target.id
        }
    }

    return dependencyNode
}
