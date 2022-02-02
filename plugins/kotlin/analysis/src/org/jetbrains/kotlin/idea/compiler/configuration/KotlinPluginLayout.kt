// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.SystemProperties
import com.intellij.util.io.URLUtil
import com.intellij.util.io.isDirectory
import com.intellij.util.io.isFile
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPathsProvider.KOTLIN_DIST_ARTIFACT_ID
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPathsProvider.KOTLIN_MAVEN_GROUP_ID
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPathsProvider.resolveMavenArtifactInMavenRepo
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.nameWithoutExtension

sealed interface KotlinPluginLayout {
    val kotlinc: File
    val jpsPluginJar: File

    val bundledKotlincVersion get() = kotlinc.resolve("build.txt").readText().trim()

    companion object {
        const val KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID = "kotlin-jps-plugin-classpath"

        fun getInstance(): KotlinPluginLayout {
            return if (PluginManagerCore.isRunningFromSources() && !System.getProperty("idea.use.dev.build.server", "false").toBoolean()) {
                KotlinPluginLayoutWhenRunFromSources
            } else {
                val jarInsideLib = PathManager.getJarPathForClass(KotlinPluginLayout::class.java)
                    ?.let { File(it) }
                    ?: error("Can't find jar file for ${KotlinPluginLayout::class.simpleName}")
                check(jarInsideLib.extension == "jar") { "$jarInsideLib should be jar file" }
                KotlinPluginLayoutWhenRunInProduction(
                    jarInsideLib
                        .parentFile
                        .also { check(it.name == "lib") { "$it should be lib directory" } }
                        .parentFile
                )
            }
        }
    }
}

private class KotlinPluginLayoutWhenRunInProduction(private val root: File) : KotlinPluginLayout {
    init {
        check(root.exists()) { "$root doesn't exist" }
    }

    private fun resolve(path: String) = root.resolve(path).also { check(it.exists()) { "$it doesn't exist" } }

    override val kotlinc: File get() = resolve("kotlinc")
    override val jpsPluginJar: File get() = resolve("lib/jps/kotlin-jps-plugin.jar")
}

private object KotlinPluginLayoutWhenRunFromSources : KotlinPluginLayout {
    private val bundledJpsVersion by lazy {
        val ideaDirectory = Paths.get(PathManager.getHomePath(), Project.DIRECTORY_STORE_FOLDER)
        require(ideaDirectory.isDirectory()) { "Can't find IDEA home directory" }

        val distLibraryFile = ideaDirectory.resolve("libraries/kotlinc_kotlin_dist.xml")
        require(distLibraryFile.isFile()) { "${distLibraryFile.nameWithoutExtension} library is not found in $ideaDirectory" }

        val distLibraryElement = JDOMUtil.load(distLibraryFile).getChild("library")
            ?: error("Can't find the 'library' element in ${distLibraryFile}")

        val propertiesElement = distLibraryElement.getChild("properties")
        if (propertiesElement != null) {
            propertiesElement.getAttributeValue("maven-id")
                ?.split(':')
                ?.takeIf { it.size == 3 }
                ?.last()
                ?: error("${distLibraryFile} is not a valid Maven library")
        } else {
            // In cooperative mode, Kotlin compiler artifacts are not Maven libraries
            val rootUrl = distLibraryElement.getChild("CLASSES")?.getChild("root")
                ?.getAttributeValue("url")
                ?: error("Can't find a valid 'CLASSES' root in ${distLibraryFile}")

            val rootPath = URLUtil.splitJarUrl(rootUrl)?.first ?: error("Root URL (${rootUrl}) is not a valid JAR URL")
            val rootPathChunks = rootPath.split('/').asReversed()
            check(rootPathChunks.size > 3 && rootPathChunks[0].startsWith(rootPathChunks[2] + "-" + rootPathChunks[1])) {
                "Unsupported root path, expected a path inside a Maven repository: $rootPathChunks"
            }

            rootPathChunks[1] // artifact version
        }
    }

    override val kotlinc: File by lazy {
        val mavenLocalDirectory = File(SystemProperties.getUserHome(), ".m2/repository")
            .takeIf { it.exists() }
            ?: error("Can't find Maven Local directory")

        // IDEA should have downloaded the library as a part of dependency resolution in the 'kotlin.util.compiler-dependencies' module

        val packedDist = resolveMavenArtifactInMavenRepo(mavenLocalDirectory, KOTLIN_DIST_ARTIFACT_ID, bundledJpsVersion)
            .takeIf { it.exists() }
            ?: error("Can't find artifact $KOTLIN_MAVEN_GROUP_ID:$KOTLIN_DIST_ARTIFACT_ID:$bundledJpsVersion artifact in Maven Local")

        KotlinPathsProvider.lazyUnpackKotlincDist(packedDist, KotlinCompilerVersion.VERSION)
    }

    override val jpsPluginJar: File
        get() = KotlinPathsProvider.getExpectedMavenArtifactJarPath(
            KotlinPluginLayout.KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID,
            bundledJpsVersion
        )
}
