// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.roots.DependencyScope
import org.jetbrains.kotlin.gradle.idea.tcs.*
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

fun DataNode<GradleSourceSetData>.addDependency(dependency: IdeaKotlinDependency): List<DataNode<out AbstractDependencyData<*>>> {
    return when (dependency) {
        is IdeaKotlinBinaryDependency -> listOfNotNull(addDependency(dependency))
        is IdeaKotlinSourceDependency -> listOfNotNull(addDependency(dependency))
        is IdeaKotlinProjectArtifactDependency -> addDependency(dependency)
    }
}

fun DataNode<GradleSourceSetData>.addDependency(dependency: IdeaKotlinSourceDependency): DataNode<ModuleDependencyData>? {
    /* Already created dependency: Return node */
    findModuleDependencyNode(dependency.kotlinSourceSetModuleId)?.let { return it }

    /* Create module dependency */
    val projectNode = ExternalSystemApiUtil.findParent(this, ProjectKeys.PROJECT) ?: return null
    val dependencyNode = projectNode.findSourceSetNode(dependency.kotlinSourceSetModuleId) ?: return null

    val moduleDependencyData = ModuleDependencyData(this.data, dependencyNode.data)
    moduleDependencyData.scope = DependencyScope.COMPILE
    return createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData)
}


fun DataNode<GradleSourceSetData>.addDependency(dependency: IdeaKotlinBinaryDependency): DataNode<LibraryDependencyData>? {
    val dependencyNode = findLibraryDependencyNode(dependency) ?: run create@{
        val coordinates = dependency.coordinates ?: return null
        val libraryData = LibraryData(KotlinLibraryName(coordinates))
        libraryData.setGroup(coordinates.group)
        libraryData.artifactId = coordinates.module
        libraryData.version = coordinates.version
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

fun DataNode<GradleSourceSetData>.addDependency(dependency: IdeaKotlinProjectArtifactDependency): List<DataNode<ModuleDependencyData>> {
    val project = this.getParent(ProjectData::class.java) ?: return emptyList()
    return KotlinProjectArtifactDependencyResolver(project).resolve(dependency).mapNotNull { sourceDependency ->
        addDependency(sourceDependency)
    }
}
