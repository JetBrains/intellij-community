// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import org.jetbrains.kotlin.idea.base.plugin.artifacts.AdditionalKotlinArtifacts.downloadArtifact
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts.Companion.OLD_FAT_JAR_KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.OLD_KOTLIN_DIST_ARTIFACT_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.LazyZipUnpacker
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

val isRunningFromSources: Boolean by lazy {
    val ideaDirectory = Paths.get(PathManager.getHomePath(), Project.DIRECTORY_STORE_FOLDER)
    Files.isDirectory(ideaDirectory) && !System.getProperty("idea.use.dev.build.server", "false").toBoolean()
}

sealed class KotlinPluginLayout {
    /**
     * Directory with the bundled Kotlin compiler distribution. Includes the compiler itself and a set of compiler plugins
     * with a compatible version.
     */
    abstract val kotlinc: File

    /**
     * Location of the JPS plugin and all its dependencies jars
     */
    abstract val jpsPluginClasspath: List<File>

    val jsEngines by lazy {
        kotlinc.resolve("lib").resolve("js.engines.jar").also { check(it.exists()) { "$it doesn't exist" } }
    }

    /**
     * Version of the stand-alone compiler (artifacts in the 'kotlinc/' directory of the Kotlin plugin).
     * Stand-alone compiler is always stable in 'master' and release branches. It is used for compilation with JPS.
     */
    val standaloneCompilerVersion: IdeKotlinVersion by lazy {
        val rawVersion = kotlinc.resolve("build.txt").readText().trim()
        IdeKotlinVersion.get(rawVersion)
    }

    /**
     * Version of the compiler's analyzer bundled into the Kotlin IDE plugin ('kotlin-compiler-for-ide' and so on).
     * Used solely for IDE code insight. Might have a pre-release version higher than `standaloneCompilerVersion`.
     */
    val ideCompilerVersion: IdeKotlinVersion = IdeKotlinVersion.get(KotlinCompilerVersion.VERSION)

    companion object {
        @JvmStatic
        val instance: KotlinPluginLayout by lazy {
            val layout = if (isRunningFromSources) {
                KotlinPluginLayoutWhenRunFromSources()
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

            assert(layout.standaloneCompilerVersion <= layout.ideCompilerVersion)
            return@lazy layout
        }

        val KOTLIN_JPS_PLUGIN_CLASSPATH = listOf(
            "lib/jps/kotlin-jps-plugin.jar" to OLD_FAT_JAR_KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID,
        )
    }
}

private class KotlinPluginLayoutWhenRunInProduction(private val kotlinPluginRoot: File) : KotlinPluginLayout() {
    init {
        check(kotlinPluginRoot.exists()) { "$kotlinPluginRoot doesn't exist" }
    }

    override val kotlinc: File by lazy { resolve("kotlinc") }

    override val jpsPluginClasspath: List<File> by lazy {
        KOTLIN_JPS_PLUGIN_CLASSPATH.map { (jar, _) -> resolve(jar) }
    }

    private fun resolve(path: String) = kotlinPluginRoot.resolve(path).also { check(it.exists()) { "$it doesn't exist" } }
}

private class KotlinPluginLayoutWhenRunFromSources : KotlinPluginLayout() {
    companion object {
        private const val KOTLINC_DIST_LIBRARY = "kotlinc_kotlin_dist.xml"
    }

    private val bundledJpsVersion by lazy {
        KotlinMavenUtils.findLibraryVersion(KOTLINC_DIST_LIBRARY)
            ?: error("Cannot find version of kotlin-dist library")
    }

    override val kotlinc: File by lazy {
        val distJar = downloadArtifact(libraryFileName = KOTLINC_DIST_LIBRARY, artifactId = OLD_KOTLIN_DIST_ARTIFACT_ID)
        LazyZipUnpacker(KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX.resolve("kotlinc-dist-for-ide-from-sources")).lazyUnpack(distJar)
    }

    override val jpsPluginClasspath: List<File> by lazy {
        KOTLIN_JPS_PLUGIN_CLASSPATH.map { (_, artifactId) ->
            KotlinMavenUtils.findArtifactOrFail(KOTLIN_MAVEN_GROUP_ID, artifactId, bundledJpsVersion).toFile()
        }
    }
}
