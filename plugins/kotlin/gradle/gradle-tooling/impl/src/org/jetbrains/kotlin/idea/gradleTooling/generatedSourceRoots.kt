// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleTooling

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.jetbrains.kotlin.idea.gradleTooling.reflect.KotlinSourceSetReflection
import java.nio.file.Path

internal fun Project.getKotlinSourceSetGeneratedSourceRoots(): List<Path> {
    val kotlinExtension = project.extensions.findByName("kotlin") ?: return emptyList()
    val kotlinExtensionClass = kotlinExtension.javaClass
    val getSourceSets = kotlinExtensionClass.getMethodOrNull("getSourceSets") ?: return emptyList()
    val sourceSets = getSourceSets.invoke(kotlinExtension) as NamedDomainObjectCollection<*>
    val generatedSourceRoots = sourceSets.map { it as Named }.fold(mutableListOf<Path>()) { acc, named ->
        named.getKotlinSourceSetGeneratedSourceRoots()?.let { acc.addAll(it) }
        acc
    }
    return generatedSourceRoots.toList()
}

private fun Named.getKotlinSourceSetGeneratedSourceRoots(): List<Path>? {
    return KotlinSourceSetReflection(this).generatedSources?.srcDirs?.map {
        @Suppress("IO_FILE_USAGE") // Gradle API
        it.toPath()
    }
}
