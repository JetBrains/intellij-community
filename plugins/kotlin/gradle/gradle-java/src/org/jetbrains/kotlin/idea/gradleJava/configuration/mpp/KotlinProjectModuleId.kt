// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(UnsafeApi::class)

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinProjectCoordinates
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.KotlinModuleUtils.fullName
import org.jetbrains.kotlin.idea.projectModel.KotlinComponent
import org.jetbrains.kotlin.tooling.core.UnsafeApi
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

fun KotlinProjectModuleId(coordinates: IdeaKotlinProjectCoordinates): KotlinProjectModuleId {
    /* Own build */
    return if (coordinates.buildPath == ":") {
        /* Root project */
        if (coordinates.projectPath == ":") {
            KotlinProjectModuleId(coordinates.projectName)
        }
        /* Subproject */
        else {
            KotlinProjectModuleId(coordinates.projectPath)
        }
    }
    /* Included build */
    else {
        /* Root project in included build */
        if (coordinates.projectPath == ":") {
            KotlinProjectModuleId(coordinates.buildPath)
        }
        /* Subproject in included build */
        else {
            KotlinProjectModuleId("${coordinates.buildPath}${coordinates.projectPath}")
        }
    }
}
