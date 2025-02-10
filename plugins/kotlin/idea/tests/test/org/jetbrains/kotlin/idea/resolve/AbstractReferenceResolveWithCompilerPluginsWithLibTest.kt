// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.resolve

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPlugins
import java.io.File
import kotlin.io.path.absolutePathString

abstract class AbstractReferenceResolveWithCompilerPluginsWithLibTest : AbstractReferenceResolveWithLibTest() {
    override val libCompilationClasspath: List<File>
        get() = project.resolveAdditionalLibClasspath()

    override val libCompilationOptions: List<String>
        get() = additionalLibCompilationOptions
}

abstract class AbstractReferenceResolveWithCompilerPluginsWithCompiledLibTest : AbstractReferenceResolveWithCompiledLibTest() {
    override val libCompilationClasspath: List<File>
        get() = project.resolveAdditionalLibClasspath()

    override val libCompilationOptions: List<String>
        get() = additionalLibCompilationOptions
}

abstract class AbstractReferenceResolveWithCompilerPluginsWithCrossLibTest : AbstractReferenceResolveWithCrossLibTest() {
    override val libCompilationClasspath: List<File>
        get() = project.resolveAdditionalLibClasspath()

    override val libCompilationOptions: List<String>
        get() = additionalLibCompilationOptions
}

const val KOTLINX_SERIALIZATION_CORE_JVM_MAVEN_COORDINATES = "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.5.0"

private fun Project.resolveAdditionalLibClasspath(): List<File> {
    val dependencyCoordinates = listOf(
        KOTLINX_SERIALIZATION_CORE_JVM_MAVEN_COORDINATES
    )

    return dependencyCoordinates.map(::loadSingleJarFromMaven)
}

private val additionalLibCompilationOptions: List<String>
    get() {
        /*
        We're using KotlinK2BundledCompilerPlugins to get the bundled jar of the plugin
        and not some random (possibly incompatible) Maven version.
        The plugin jar should contain both K1 and K2 plugin implementations.
        */
        val compilerPlugins = listOf(
            KotlinK2BundledCompilerPlugins.KOTLINX_SERIALIZATION_COMPILER_PLUGIN
        )

        return compilerPlugins.map { "-Xplugin=${it.bundledJarLocation.absolutePathString()}" }
    }

fun Project.loadSingleJarFromMaven(mavenCoordinates: String): File {
    val jarDescriptor = RepositoryLibraryProperties(mavenCoordinates, /* includeTransitiveDependencies = */ false)

    val dependencyRoots = JarRepositoryManager.loadDependenciesModal(
        this,
        jarDescriptor,
        /* loadSources = */ false,
        /* loadJavadoc = */ false,
        /* copyTo = */ null,
        /* repositories = */ emptyList()
    )

    val singleRoot = dependencyRoots.singleOrNull()
        ?: error("Maven library without transitive dependencies should only a single root: $dependencyRoots")

    val pathToJar = singleRoot.file.path.removeSuffix("!/")

    return File(pathToJar).also { require(it.exists()) }
}