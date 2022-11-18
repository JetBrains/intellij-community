// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(UnsafeApi::class)

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinSourceCoordinates
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.KotlinModuleUtils.fullName
import org.jetbrains.kotlin.idea.projectModel.KotlinComponent
import org.jetbrains.kotlin.tooling.core.UnsafeApi
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

@JvmInline
value class KotlinProjectModuleId @UnsafeApi constructor(private val id: String) {
    operator fun plus(kotlinComponent: KotlinComponent): KotlinSourceSetModuleId =
        this + kotlinComponent.fullName()

    operator fun plus(sourceSetName: String): KotlinSourceSetModuleId =
        KotlinSourceSetModuleId(this.id + ":" + sourceSetName)
}


fun KotlinProjectModuleId(resolverContext: ProjectResolverContext, gradleIdeaModule: GradleIdeaModule) =
    KotlinProjectModuleId(GradleProjectResolverUtil.getModuleId(resolverContext, gradleIdeaModule))

fun KotlinProjectModuleId(coordinates: IdeaKotlinSourceCoordinates): KotlinProjectModuleId {
    return KotlinProjectModuleId(GradleProjectResolverUtil.getModuleId(coordinates.projectPath, coordinates.projectName))
}

@OptIn(UnsafeApi::class)
val ModuleData.kotlinProjectModuleId: KotlinProjectModuleId get() = KotlinProjectModuleId(this.id)

@Deprecated(
    message = "This is a SourceSet module! Use '.kotlinSourceSetModuleId' instead!",
    replaceWith = ReplaceWith("kotlinSourceSetModuleId")
)
@Suppress("unused") /* This is a helper to provide guidance */
val GradleSourceSetData.kotlinProjectModuleId: KotlinSourceSetModuleId get() = KotlinSourceSetModuleId(id)


fun DataNode<*>.findProjectModuleNode(id: KotlinProjectModuleId): DataNode<ModuleData>? {
    @Suppress("unchecked_cast")
    return ExternalSystemApiUtil.findFirstRecursively(this) { node ->
        val data = node.data
        data is ModuleData && data.kotlinProjectModuleId == id
    } as? DataNode<ModuleData>
}
