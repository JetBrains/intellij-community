// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.AbstractDependencyData
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData
import com.intellij.openapi.externalSystem.model.project.LibraryLevel
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinResolvedBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinUnresolvedBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.extras.documentationClasspath
import org.jetbrains.kotlin.gradle.idea.tcs.extras.isIdeaProjectLevel
import org.jetbrains.kotlin.gradle.idea.tcs.extras.isNativeDistribution
import org.jetbrains.kotlin.gradle.idea.tcs.extras.isNativeStdlib
import org.jetbrains.kotlin.gradle.idea.tcs.extras.klibExtra
import org.jetbrains.kotlin.gradle.idea.tcs.extras.sourcesClasspath
import org.jetbrains.kotlin.gradle.idea.tcs.isKotlinCompileBinaryType
import org.jetbrains.kotlin.idea.gradle.configuration.klib.KotlinNativeLibraryNameUtil.KOTLIN_NATIVE_LIBRARY_PREFIX
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.ifNull
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_NAME
import java.io.File
import java.nio.file.Path

fun DataNode<GradleSourceSetData>.addDependency(dependency: IdeaKotlinBinaryDependency): DataNode<out LibraryDependencyData>? {

    val dependencyNode = findLibraryDependencyNode(dependency) ?: run create@{
        val coordinates = dependency.coordinates ?: return null
        val libraryData = LibraryData(KotlinLibraryName(coordinates), isUnresolved = dependency is IdeaKotlinUnresolvedBinaryDependency)
        val libraryLevel = if (dependency.isIdeaProjectLevel) LibraryLevel.PROJECT else LibraryLevel.MODULE
        libraryData.setGroup(coordinates.group)
        libraryData.artifactId = coordinates.module
        libraryData.version = coordinates.version
        libraryData.internalName = "$GRADLE_NAME: ${coordinates.displayString}"
        createChild(ProjectKeys.LIBRARY_DEPENDENCY, LibraryDependencyData(this.data, libraryData, libraryLevel))
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
                addToDependencyNode(file, dependencyNode, LibraryPathType.BINARY)
            }
        }

        dependency.sourcesClasspath.forEach { file ->
            addToDependencyNode(file, dependencyNode, LibraryPathType.SOURCE)
        }

        dependency.documentationClasspath.forEach { file ->
            addToDependencyNode(file, dependencyNode, LibraryPathType.DOC)
        }
    }

    /*
    Handle dependencies that are marked as 'project level'.
    Those dependencies are not bound to the particular SourceSet, unless the binaries differ (see [tryLinkProjectLibrary]).
    This block needs the final name and roots of the library.
     */
    if (dependency.isIdeaProjectLevel) {
        val projectNode = getParent(ProjectData::class.java)
        val library = dependencyNode.data.target
        dependencyNode.data.level = when {
            dependency.isNativeDistribution -> {
                // No module-level fallback: native distribution libraries must stay project-level (see [KotlinNativeLibrariesFixer])
                GradleProjectResolverUtil.linkProjectLibrary(projectNode, library)
                LibraryLevel.PROJECT
            }

            projectNode != null && tryLinkProjectLibrary(projectNode, library) -> LibraryLevel.PROJECT
            else -> LibraryLevel.MODULE
        }
    }

    return dependencyNode
}

private val PROJECT_LIBRARIES_BY_INTERNAL_NAME: Key<MutableMap<String, LibraryData>> =
    Key.create("KotlinMpp.PROJECT_LIBRARIES_BY_INTERNAL_NAME")

/**
 * Returns `true` if the dependency on [library] can use the project library with the same internal name.
 * - If the project has no library with this internal name, [library] becomes the project library.
 * - If the project library has all binaries of [library], the dependency can use it.
 * - In all other cases, the function returns `false`.
 */
private fun tryLinkProjectLibrary(projectNode: DataNode<ProjectData>, library: LibraryData): Boolean {
    val projectLibraries = projectNode.putUserDataIfAbsent(PROJECT_LIBRARIES_BY_INTERNAL_NAME, HashMap())
    val projectLibrary = projectLibraries.getOrPut(library.internalName) {
        ExternalSystemApiUtil.findChild(projectNode, ProjectKeys.LIBRARY) { it.data.internalName == library.internalName }?.data
            ?: library.takeIf { GradleProjectResolverUtil.linkProjectLibrary(projectNode, library) }
            ?: return false
    }
    return projectLibrary.getPaths(LibraryPathType.BINARY).containsAll(library.getPaths(LibraryPathType.BINARY))
}

/**
 * Replaces the target of each project-level library dependency in [dependencyNodes] with the project library of the same internal name,
 * so memory usage inside data nodes is reduced.
 *
 * This function should be called only when the source set has all its dependencies.
 */
internal fun shareProjectLibraryTargets(
    projectNode: DataNode<ProjectData>,
    dependencyNodes: List<DataNode<out AbstractDependencyData<*>>>
) {
    val projectLibraries = projectNode.putUserDataIfAbsent(PROJECT_LIBRARIES_BY_INTERNAL_NAME, HashMap())
    for (dependencyNode in dependencyNodes) {
        val dependency = dependencyNode.data as? LibraryDependencyData ?: continue
        if (dependency.level != LibraryLevel.PROJECT) continue
        val internalName = dependency.target.internalName
        val projectLibrary = projectLibraries[internalName]
            ?: ExternalSystemApiUtil.findChild(projectNode, ProjectKeys.LIBRARY) { it.data.internalName == internalName }?.data
                ?.also { projectLibraries[internalName] = it }
            ?: continue
        dependency.target = projectLibrary
    }
}

private fun addToDependencyNode(
    file: File,
    dependencyNode: DataNode<out LibraryDependencyData>,
    libraryPathType: LibraryPathType
) {
    refreshInVFSIfNecessary(file.toPath().toAbsolutePath())
    dependencyNode.data.target.addPath(libraryPathType, file.absolutePath)
}

private fun refreshInVFSIfNecessary(absolutePath: Path) {
    val virtualFileManager = VirtualFileManager.getInstance()
    if (virtualFileManager.findFileByNioPath(absolutePath) == null) {
        virtualFileManager.refreshAndFindFileByNioPath(absolutePath)
    }
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
