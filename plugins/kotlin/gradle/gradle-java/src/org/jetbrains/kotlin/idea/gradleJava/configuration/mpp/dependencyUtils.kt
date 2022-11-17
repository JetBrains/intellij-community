// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.roots.DependencyScope
import org.jetbrains.kotlin.gradle.idea.tcs.*
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

fun DataNode<GradleSourceSetData>.addDependency(dependency: IdeaKotlinDependency): DataNode<out AbstractDependencyData<*>>? {
    return when (dependency) {
        is IdeaKotlinBinaryDependency -> addDependency(dependency)
        is IdeaKotlinSourceDependency -> addDependency(dependency)
    }
}

fun DataNode<GradleSourceSetData>.addDependency(dependency: IdeaKotlinSourceDependency): DataNode<ModuleDependencyData>? {
    val projectNode = ExternalSystemApiUtil.findParent(this, ProjectKeys.PROJECT) ?: return null
    val dependencyNode = projectNode.findSourceSetNode(dependency.coordinates) ?: return null
    val existing = ExternalSystemApiUtil.findAll(this, ProjectKeys.MODULE_DEPENDENCY)
        .firstOrNull { node -> node.data.target.id == dependencyNode.data.id }
    if (existing != null) return existing

    val moduleDependencyData = ModuleDependencyData(this.data, dependencyNode.data)
    moduleDependencyData.scope = DependencyScope.COMPILE
    return createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData)
}


fun DataNode<GradleSourceSetData>.addDependency(dependency: IdeaKotlinBinaryDependency): DataNode<LibraryDependencyData>? {
    val dependencyCoordinates = dependency.coordinates ?: return null
    val libraryName = KotlinLibraryName(dependencyCoordinates)

    val dependencyNode = findLibraryDependencyNode(libraryName) ?: run create@{
        val libraryData = LibraryData(libraryName)
        libraryData.setGroup(dependencyCoordinates.group)
        libraryData.artifactId = dependencyCoordinates.module
        libraryData.version = dependencyCoordinates.version
        createChild(ProjectKeys.LIBRARY_DEPENDENCY, LibraryDependencyData(this.data, libraryData, LibraryLevel.MODULE))
    }

    if (dependency is IdeaKotlinResolvedBinaryDependency) {
        val pathType = when (dependency.binaryType) {
            IdeaKotlinDependency.CLASSPATH_BINARY_TYPE -> LibraryPathType.BINARY
            IdeaKotlinDependency.SOURCES_BINARY_TYPE -> LibraryPathType.SOURCE
            IdeaKotlinDependency.DOCUMENTATION_BINARY_TYPE -> LibraryPathType.DOC
            else -> null
        }

        if (pathType != null) {
            dependencyNode.data.target.addPath(pathType, dependency.binaryFile.absolutePath)
        }
    }

    return dependencyNode
}
