// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(UnsafeApi::class)

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryCoordinates
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryDependency
import org.jetbrains.kotlin.tooling.core.UnsafeApi
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants

@JvmInline
value class KotlinLibraryName @UnsafeApi constructor(val name: String) {
    override fun toString(): String = name
}

fun KotlinLibraryName(coordinates: IdeaKotlinBinaryCoordinates): KotlinLibraryName {
    return KotlinLibraryName(
        buildString {
            append(coordinates.group)
            append(":")
            append(coordinates.module)
            if (coordinates.sourceSetName != null) {
                append(":")
                append(coordinates.sourceSetName)
            }
            if (coordinates.version != null) {
                append(":")
                append(coordinates.version)
            }
        }
    )
}

val LibraryData.kotlinLibraryName: KotlinLibraryName get() = KotlinLibraryName(this.externalName)

fun LibraryData(name: KotlinLibraryName) = LibraryData(GradleConstants.SYSTEM_ID, name.toString())
