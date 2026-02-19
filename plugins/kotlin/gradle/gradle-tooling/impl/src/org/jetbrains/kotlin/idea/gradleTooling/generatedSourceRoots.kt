// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinSourceSetReflection
import java.nio.file.Path

// KotlinSourceSet.generatedKotlin was introduced in Kotlin Gradle plugin 2.3.0
private const val MIN_KOTLIN_PLUGIN_VERSION = "2.3.0"

internal fun Project.getKotlinSourceSetGeneratedSourceRoots(
    kotlinPluginVersion: KotlinGradlePluginVersion?
): List<Path> {
    val kotlinExtension = project.extensions.findByName("kotlin") ?: return emptyList()
    val kotlinExtensionClass = kotlinExtension.javaClass
    val getSourceSets = kotlinExtensionClass.getMethodOrNull("getSourceSets") ?: return emptyList()
    val sourceSets = getSourceSets.invoke(kotlinExtension) as NamedDomainObjectCollection<*>
    val generatedSourceRoots = sourceSets.map { KotlinSourceSetReflection(it as Named) }.flatMap {
        it.getKotlinSourceSetGeneratedSourceRoots(kotlinPluginVersion)
    }
    return generatedSourceRoots.toList()
}

internal fun KotlinSourceSetReflection.getKotlinSourceSetGeneratedSourceRoots(
    kotlinPluginVersion: KotlinGradlePluginVersion?
): List<Path> {
    return if (kotlinPluginVersion == null || kotlinPluginVersion < MIN_KOTLIN_PLUGIN_VERSION) {
        emptyList()
    } else {
        generatedSources?.srcDirs?.map {
            @Suppress("IO_FILE_USAGE") // Gradle API
            it.toPath()
        } ?: emptyList()
    }
}
