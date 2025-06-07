// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.ide.plugins.getPluginDistDirByClass
import com.intellij.idea.AppMode
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.OLD_FAT_JAR_KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.OLD_KOTLIN_DIST_ARTIFACT_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.LazyZipUnpacker
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader.downloadArtifactForIdeFromSources
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.exists

@get:ApiStatus.Internal
val isRunningFromSources: Boolean
    get() = KotlinPluginLayoutModeProvider.kotlinPluginLayoutMode == KotlinPluginLayoutMode.SOURCES

object KotlinPluginLayoutModeProvider {
    @Volatile
    private var forcedMode: AtomicReference<KotlinPluginLayoutMode?> = AtomicReference(null)

    private val lazyMode = lazy {
        forcedMode.get() ?: computeDefaultMode()
    }

    private fun computeDefaultMode(): KotlinPluginLayoutMode {
        val isRunningFromSources =
            !AppMode.isDevServer() && Files.isDirectory(Path.of(PathManager.getHomePath(), Project.DIRECTORY_STORE_FOLDER))
        return if (isRunningFromSources) KotlinPluginLayoutMode.SOURCES else KotlinPluginLayoutMode.INTELLIJ
    }

    @get:ApiStatus.Internal
    val kotlinPluginLayoutMode: KotlinPluginLayoutMode by lazyMode

    @ApiStatus.Internal
    fun setForcedKotlinPluginLayoutMode(mode: KotlinPluginLayoutMode) {
        if (lazyMode.isInitialized()) {
            error("Cannot set forced mode after lazy initialization: ${lazyMode.value} -> $mode")
        }
        if (!forcedMode.compareAndSet(null, mode)) {
            error("Forced mode is already set to ${forcedMode.get()}")
        }
    }
}

@ApiStatus.Internal
enum class KotlinPluginLayoutMode {
    SOURCES,
    INTELLIJ,
    LSP,
}

object KotlinPluginLayout {
    @get:ApiStatus.ScheduledForRemoval
    @Deprecated("Use 'KotlinPluginLayout' directly", ReplaceWith("KotlinPluginLayout"))
    @get:Deprecated("Use 'KotlinPluginLayout' directly", ReplaceWith("KotlinPluginLayout"))
    @JvmStatic
    val instance: KotlinPluginLayout
        get() = KotlinPluginLayout

    /**
     * Directory with the bundled Kotlin compiler distribution. Includes the compiler itself and a set of compiler plugins
     * with a compatible version.
     */
    @JvmStatic
    val kotlinc: File
        get() = kotlincProvider.value

    /**
     * Directory with the Kotlin compiler distribution same version as plugin analyzer.
     */
    @Deprecated(
        "Use 'kotlinc' instead. Temporary workaround for scratches before Kotlin 2.1.20",
        ReplaceWith("kotlinc")
    )
    @JvmStatic
    // TODO: KTIJ-32993
    val kotlincIde: File
        get() = kotlincIdeProvider.value

    /**
     * Location of the JPS plugin and all its dependency jars
     */
    val jpsPluginClasspath: List<File>
        get() = jpsPluginClasspathProvider.value

    val jsEngines: File by lazy {
        kotlinc.resolve("lib").resolve("js.engines.jar").also { check(it.exists()) { "$it doesn't exist" } }
    }

    /**
     * Version of the stand-alone compiler (artifacts in the 'kotlinc/' directory of the Kotlin plugin).
     * Stand-alone compiler is always stable in 'master' and release branches. It is used for compilation with JPS.
     */
    @JvmStatic
    val standaloneCompilerVersion: IdeKotlinVersion
        get() = standaloneCompilerVersionProvider.value

    /**
     * Version of the compiler's analyzer bundled into the Kotlin IDE plugin ('kotlin-compiler-for-ide' and so on).
     * Used solely for IDE code insight. Might have a pre-release version higher than `standaloneCompilerVersion`.
     */
    @JvmStatic
    val ideCompilerVersion: IdeKotlinVersion = IdeKotlinVersion.get(KotlinCompilerVersion.VERSION)

    private val kotlincProvider: Lazy<File>
    private val kotlincIdeProvider: Lazy<File>
    private val jpsPluginClasspathProvider: Lazy<List<File>>
    private val standaloneCompilerVersionProvider: Lazy<IdeKotlinVersion>

    init {
         val standaloneCompilerVersionDefaultProvider = lazy {
            val rawVersion = kotlinc.resolve("build.txt").readText().trim()
            IdeKotlinVersion.get(rawVersion)
        }
        when (KotlinPluginLayoutModeProvider.kotlinPluginLayoutMode) {
            KotlinPluginLayoutMode.SOURCES -> {
                val bundledJpsVersion by lazy {
                    KotlinMavenUtils.findLibraryVersion("kotlinc_kotlin_dist.xml")
                }

                kotlincProvider = lazy {
                    @Suppress("DEPRECATION")
                    val distJar = downloadArtifactForIdeFromSources(
                        OLD_KOTLIN_DIST_ARTIFACT_ID,
                        KotlinMavenUtils.findLibraryVersion("kotlinc_kotlin_dist.xml")
                    ) ?: error("Can't download dist")
                    val unpackedDistDir = KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX.resolve("kotlinc-dist-for-ide-from-sources")
                    LazyZipUnpacker(unpackedDistDir).lazyUnpack(distJar)
                }

                // TODO: KTIJ-32993
                kotlincIdeProvider = lazy {
                    @Suppress("DEPRECATION")
                    val distJar = downloadArtifactForIdeFromSources(
                        OLD_KOTLIN_DIST_ARTIFACT_ID,
                        KotlinMavenUtils.findLibraryVersion("kotlinc_kotlin_ide_dist.xml")
                    ) ?: error("Can't download dist")
                    val unpackedDistDir =
                        KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX.resolve("kotlinc-ide-dist-for-ide-from-sources")
                    LazyZipUnpacker(unpackedDistDir).lazyUnpack(distJar)
                }

                jpsPluginClasspathProvider = lazy {
                    @Suppress("DEPRECATION")
                    val jpsPluginArtifact = KotlinMavenUtils.findArtifactOrFail(
                        KOTLIN_MAVEN_GROUP_ID,
                        OLD_FAT_JAR_KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID,
                        bundledJpsVersion
                    )

                    listOf(jpsPluginArtifact.toFile())
                }
                standaloneCompilerVersionProvider = standaloneCompilerVersionDefaultProvider
            }

            KotlinPluginLayoutMode.INTELLIJ -> {
                val kotlinPluginRoot = getPluginDistDirByClass(KotlinPluginLayout::class.java)
                    ?: error("Can't find jar file for ${KotlinPluginLayout::class.simpleName}")

                fun resolve(path: String) = kotlinPluginRoot.resolve(path).also { check(it.exists()) { "$it doesn't exist" } }.toFile()

                kotlincProvider = lazy { resolve("kotlinc") }
                kotlincIdeProvider = lazy { resolve("kotlinc.ide") }
                jpsPluginClasspathProvider = lazy { listOf(resolve("lib/jps/kotlin-jps-plugin.jar")) }
                standaloneCompilerVersionProvider = standaloneCompilerVersionDefaultProvider
            }

            KotlinPluginLayoutMode.LSP -> {
                kotlincProvider = lazy { error("LSP doesn't not include kotlinc") }
                kotlincIdeProvider = lazy { error("LSP doesn't not include kotlinc.ide for scripts") }
                jpsPluginClasspathProvider = lazy { error("LSP doesn't not include jps compiler") }
                standaloneCompilerVersionProvider = lazy { ideCompilerVersion /*there is no standalone compiler in LSP */}
            }
        }

        check(standaloneCompilerVersion <= ideCompilerVersion) {
            "standaloneCompilerVersion: $standaloneCompilerVersion, ideCompilerVersion: $ideCompilerVersion"
        }
    }
}