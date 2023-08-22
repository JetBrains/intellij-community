// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryCoordinates
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinSourceDependency
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData


fun DataNode<GradleSourceSetData>.findModuleDependencyNode(id: KotlinSourceSetModuleId): DataNode<out ModuleDependencyData>? {
    return ExternalSystemApiUtil.findAll(this, ProjectKeys.MODULE_DEPENDENCY).firstOrNull { node ->
        /* Require target to be SourceSet module */
        val target = node.data.target as? GradleSourceSetData ?: return@firstOrNull false
        target.kotlinSourceSetModuleId == id
    }
}

fun DataNode<GradleSourceSetData>.findModuleDependencyNode(dependency: IdeaKotlinSourceDependency): DataNode<out ModuleDependencyData>? {
    return findModuleDependencyNode(KotlinSourceSetModuleId(dependency.coordinates))
}

fun DataNode<GradleSourceSetData>.findLibraryDependencyNode(name: KotlinLibraryName): DataNode<out LibraryDependencyData>? {
    @Suppress("unchecked_cast")
    return ExternalSystemApiUtil.findFirstRecursively(this) { node ->
        val data = node.data
        data is LibraryDependencyData && data.target.kotlinLibraryName == name
    } as? DataNode<out LibraryDependencyData>
}

fun DataNode<GradleSourceSetData>.findLibraryDependencyNode(coordinates: IdeaKotlinBinaryCoordinates): DataNode<out LibraryDependencyData>? {
    return findLibraryDependencyNode(KotlinLibraryName(coordinates))
}

fun DataNode<GradleSourceSetData>.findLibraryDependencyNode(dependency: IdeaKotlinBinaryDependency): DataNode<out LibraryDependencyData>? {
    return findLibraryDependencyNode(dependency.coordinates ?: return null)
}

internal inline fun <reified T : Any> DataNode<*>.cast(): DataNode<out T> {
    if (data !is T) {
        throw ClassCastException("DataNode<${data.javaClass.canonicalName}> cannot be cast to DataNode<${T::class.java.canonicalName}>")
    }
    @Suppress("UNCHECKED_CAST")
    return this as DataNode<T>
}

internal inline fun <reified T : Any> DataNode<*>.safeCastDataNode(): DataNode<out T>? {
    if (data !is T) return null
    @Suppress("UNCHECKED_CAST")
    return this as DataNode<T>
}
