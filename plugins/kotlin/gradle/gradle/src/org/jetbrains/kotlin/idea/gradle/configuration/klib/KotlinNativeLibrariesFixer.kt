// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradle.configuration.klib

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData
import com.intellij.openapi.externalSystem.model.project.LibraryLevel
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.kotlin.idea.gradle.configuration.klib.KotlinNativeLibraryNameUtil.GRADLE_LIBRARY_PREFIX
import org.jetbrains.kotlin.idea.gradle.configuration.klib.KotlinNativeLibraryNameUtil.KOTLIN_NATIVE_LIBRARY_PREFIX
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

/**
 * Gradle IDE plugin creates [LibraryData] nodes with internal name consisting of two parts:
 * - mandatory "Gradle: " prefix
 * - and library name
 * Then internal name is propagated to IDE [Library] object, and is displayed in IDE as "Gradle: <LIBRARY_NAME>".
 * [KotlinNativeLibrariesFixer] removes "Gradle: " prefix from all [LibraryData] items representing KLIBs to make them
 * look more friendly.
 *
 * Also, [KotlinNativeLibrariesFixer] makes sure that all KLIBs from Kotlin/Native distribution are added to IDE project model
 * as project-level libraries. This is necessary until the appropriate fix in IDEA will be implemented (for details see IDEA-211451).
 */
@IntellijInternalApi
object KotlinNativeLibrariesFixer {
    fun applyTo(ownerNode: DataNode<GradleSourceSetData>, ideProject: DataNode<ProjectData>, resolverCtx: ProjectResolverContext) {
        for (libraryDependencyNode in ExternalSystemApiUtil.findAll(ownerNode, ProjectKeys.LIBRARY_DEPENDENCY)) {
            val libraryData = libraryDependencyNode.data.target

            // Only KLIBs from Kotlin/Native distribution can have such prefix:
            if (libraryData.internalName.startsWith("$GRADLE_LIBRARY_PREFIX$KOTLIN_NATIVE_LIBRARY_PREFIX")) {
                fixLibraryName(libraryData)
                GradleProjectResolverUtil.linkProjectLibrary(ideProject, libraryData)
                fixLibraryDependencyLevel(libraryDependencyNode)
            }
        }
    }

    private fun fixLibraryName(libraryData: LibraryData) {
        libraryData.internalName = libraryData.internalName.substringAfter(GRADLE_LIBRARY_PREFIX)
    }

    private fun fixLibraryDependencyLevel(oldDependencyNode: DataNode<LibraryDependencyData>) {
        val oldDependency = oldDependencyNode.data
        if (oldDependency.level == LibraryLevel.PROJECT) return // nothing to do

        val newDependency = LibraryDependencyData(oldDependency.ownerModule, oldDependency.target, LibraryLevel.PROJECT).apply {
            scope = oldDependency.scope
            order = oldDependency.order
            isExported = oldDependency.isExported
        }

        val parentNode = oldDependencyNode.parent ?: return
        val childNodes = oldDependencyNode.children

        val newDependencyNode = parentNode.createChild(oldDependencyNode.key, newDependency)
        for (child in childNodes) {
            newDependencyNode.addChild(child)
        }

        oldDependencyNode.clear(true)
    }
}
