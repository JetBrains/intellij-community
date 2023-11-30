// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(UnsafeApi::class)

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinSourceCoordinates
import org.jetbrains.kotlin.idea.projectModel.KotlinComponent
import org.jetbrains.kotlin.tooling.core.UnsafeApi
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

internal typealias GradleIdeaModule = IdeaModule

@JvmInline
value class KotlinSourceSetModuleId @UnsafeApi constructor(private val id: String) {
    override fun toString(): String = id
}

fun KotlinSourceSetModuleId(
    resolverContext: ProjectResolverContext,
    gradleIdeaModule: GradleIdeaModule,
    kotlinComponent: KotlinComponent
) = KotlinProjectModuleId(resolverContext, gradleIdeaModule) + kotlinComponent

fun KotlinSourceSetModuleId(coordinates: IdeaKotlinSourceCoordinates): KotlinSourceSetModuleId {
    return KotlinProjectModuleId(coordinates.project) + coordinates.sourceSetName
}

@OptIn(UnsafeApi::class)
val GradleSourceSetData.kotlinSourceSetModuleId: KotlinSourceSetModuleId get() = KotlinSourceSetModuleId(id)


@Deprecated("Use 'findKotlinSourceSetDataNode' instead", replaceWith = ReplaceWith("findKotlinSourceSetDataNode"))
fun DataNode<*>.findSourceSetNode(id: KotlinSourceSetModuleId): DataNode<GradleSourceSetData>? {
    val projectNode = safeCastDataNode<ProjectData>() ?: ExternalSystemApiUtil.findParent(this, ProjectKeys.PROJECT) ?: return null
    return projectNode.findSourceSetDataNode(id)
}

fun DataNode<out ProjectData>.findSourceSetDataNode(id: KotlinSourceSetModuleId): DataNode<GradleSourceSetData>? {
    val sourceSets = getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS) ?: return null
    return sourceSets[id.toString()]?.first
}
