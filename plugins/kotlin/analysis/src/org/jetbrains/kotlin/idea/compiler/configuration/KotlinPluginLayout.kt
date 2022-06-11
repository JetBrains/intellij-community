// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.SystemProperties
import com.intellij.util.io.URLUtil
import com.intellij.util.io.isDirectory
import com.intellij.util.io.isFile
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPathsProvider.KOTLIN_DIST_ARTIFACT_ID
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPathsProvider.KOTLIN_MAVEN_GROUP_ID
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPathsProvider.resolveMavenArtifactInMavenRepo
import org.jetbrains.kotlin.psi.KtElement
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.nameWithoutExtension

sealed interface KotlinPluginLayout {
    /**
     * Directory with the bundled Kotlin compiler distribution. Includes the compiler itself and a set of compiler plugins
     * with a compatible version.
     */
    val kotlinc: File

    /**
     * Location of the JPS plugin, compatible with the bundled Kotlin compiler distribution.
     */
    val jpsPluginJar: File

    /**
     * Version of the stand-alone compiler (artifacts in the 'kotlinc/' directory of the Kotlin plugin).
     * Stand-alone compiler is always stable in 'master' and release branches. It is used for compilation with JPS.
     */
    val standaloneCompilerVersion: String
        @NlsSafe get() = kotlinc.resolve("build.txt").readText().trim()

    val lastStableKnownCompilerVersion: String
        @NlsSafe get() = "1.6.10-release-952"

    /**
     * Version of the compiler's analyzer bundled into the Kotlin IDE plugin ('kotlin-compiler-for-ide' and so on).
     * Used solely for IDE code insight. Might have a pre-release version higher than `standaloneCompilerVersion`.
     */
    val ideCompilerVersion: String
        @NlsSafe get() = KotlinCompilerVersion.VERSION

    companion object {
        const val KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID = "kotlin-jps-plugin-classpath"

        @JvmStatic
        val instance: KotlinPluginLayout by lazy {
            val ideaDirectory = Paths.get(PathManager.getHomePath(), Project.DIRECTORY_STORE_FOLDER)
            if (ideaDirectory.isDirectory() && !System.getProperty("idea.use.dev.build.server", "false").toBoolean()) {
                return@lazy KotlinPluginLayoutWhenRunFromSources(ideaDirectory)
            }

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

private class KotlinPluginLayoutWhenRunInProduction(private val kotlinPluginRoot: File) : KotlinPluginLayout {
    init {
        check(kotlinPluginRoot.exists()) { "$kotlinPluginRoot doesn't exist" }
    }

    override val kotlinc: File by lazy { resolve("kotlinc") }
    override val jpsPluginJar: File by lazy { resolve("lib/jps/kotlin-jps-plugin.jar") }

    private fun resolve(path: String) = kotlinPluginRoot.resolve(path).also { check(it.exists()) { "$it doesn't exist" } }
}

private class KotlinPluginLayoutWhenRunFromSources(private val ideaDirectory: Path) : KotlinPluginLayout {
    private val bundledJpsVersion by lazy {
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
        val stdlibFile = PathManager.getJarPathForClass(KtElement::class.java)?.let { File(it) }
            ?: error("Can't find kotlin-stdlib.jar in Maven Local")

        // IDEA should have downloaded the library as a part of dependency resolution in the 'kotlin.util.compiler-dependencies' module
        val packedDist = generateSequence(stdlibFile) { it.parentFile }
            .map { resolveMavenArtifactInMavenRepo(it, KOTLIN_DIST_ARTIFACT_ID, bundledJpsVersion) }
            .firstOrNull { it.exists() }
            ?: error(
                "Can't find artifact '$KOTLIN_MAVEN_GROUP_ID:$KOTLIN_DIST_ARTIFACT_ID:$bundledJpsVersion' in Maven Local"
            )

        KotlinPathsProvider.lazyUnpackKotlincDist(packedDist, bundledJpsVersion)
    }

    override val jpsPluginJar: File by lazy {
        KotlinPathsProvider.getExpectedMavenArtifactJarPath(
            KotlinPluginLayout.KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID,
            bundledJpsVersion
        )
    }
}
