// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData
import com.intellij.openapi.externalSystem.model.project.LibraryLevel
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.externalSystem.model.project.ProjectData
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinResolvedBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinUnresolvedBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.extras.*
import org.jetbrains.kotlin.gradle.idea.tcs.isKotlinCompileBinaryType
import org.jetbrains.kotlin.idea.gradle.configuration.klib.KotlinNativeLibraryNameUtil.KOTLIN_NATIVE_LIBRARY_PREFIX
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.ifNull
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil

fun DataNode<GradleSourceSetData>.addDependency(dependency: IdeaKotlinBinaryDependency): DataNode<out LibraryDependencyData>? {

    val dependencyNode = findLibraryDependencyNode(dependency) ?: run create@{
        val coordinates = dependency.coordinates ?: return null
        val libraryData = LibraryData(KotlinLibraryName(coordinates), isUnresolved = dependency is IdeaKotlinUnresolvedBinaryDependency)
        val libraryLevel = if (dependency.isIdeaProjectLevel) LibraryLevel.PROJECT else LibraryLevel.MODULE
        libraryData.setGroup(coordinates.group)
        libraryData.artifactId = coordinates.module
        libraryData.version = coordinates.version
        libraryData.internalName = coordinates.displayString
        createChild(ProjectKeys.LIBRARY_DEPENDENCY, LibraryDependencyData(this.data, libraryData, libraryLevel))
    }

    /*
    Handle dependencies that are marked as 'project level'.
    Those dependencies are not bound to the particular SourceSet!
     */
    if (dependency.isIdeaProjectLevel) {
        dependencyNode.data.level = LibraryLevel.PROJECT
        GradleProjectResolverUtil.linkProjectLibrary(getParent(ProjectData::class.java), dependencyNode.data.target)
    }

    /*
    Handle dependencies that are coming from the native distribution.
    Those dependencies shall receive a nicer representation (name)
     */
    if (dependency.isNativeDistribution) {
        dependencyNode.data.target.internalName = buildNativeDistributionInternalLibraryName(dependency)
    }

    if (dependency is IdeaKotlinResolvedBinaryDependency) {
        if (dependency.isKotlinCompileBinaryType) {
            dependency.classpath.forEach { file ->
                dependencyNode.data.target.addPath(LibraryPathType.BINARY, file.absolutePath)
            }
        }

        dependency.sourcesClasspath.forEach { file ->
            dependencyNode.data.target.addPath(LibraryPathType.SOURCE, file.absolutePath)
        }

        dependency.documentationClasspath.forEach { file ->
            dependencyNode.data.target.addPath(LibraryPathType.DOC, file.absolutePath)
        }
    }

    return dependencyNode
}


private fun buildNativeDistributionInternalLibraryName(dependency: IdeaKotlinBinaryDependency): String {
    return buildString {
        append(KOTLIN_NATIVE_LIBRARY_PREFIX)
        append(": ")
        append(dependency.coordinates?.module)

        /* Stdlib is always the same, does not require any suffix */
        if (!dependency.isNativeStdlib.ifNull(false)) {
            dependency.klibExtra?.also { klibExtra ->
                if (klibExtra.commonizerTarget != null) append(" | [${klibExtra.commonizerTarget}]")
                else if (klibExtra.nativeTargets != null) append(" | ${klibExtra.nativeTargets.orEmpty().joinToString(", ")}")
            }
        }
    }
}
