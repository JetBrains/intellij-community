// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.getPluginDistDirByClass
import com.intellij.idea.AppMode
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.eel.provider.utils.EelPathUtils.transferLocalContentToRemote
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.OLD_FAT_JAR_KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.OLD_KOTLIN_DIST_ARTIFACT_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.LazyZipUnpacker
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader.downloadArtifactForIdeFromSources
import org.jetbrains.kotlin.idea.testFramework.TestKotlinArtifactsProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText

@get:ApiStatus.Internal
val isRunningFromSources: Boolean
    get() = KotlinPluginLayoutModeProvider.kotlinPluginLayoutMode == KotlinPluginLayoutMode.SOURCES

object KotlinPluginLayoutModeProvider {

    private val lazyMode = lazy {
        computeDefaultMode()
    }

    private fun computeDefaultMode(): KotlinPluginLayoutMode {
        val isRunningFromSources =
            !AppMode.isRunningFromDevBuild() && Files.isDirectory(PathManager.getHomeDir().resolve(Project.DIRECTORY_STORE_FOLDER))
        return when {
            System.getProperty("kotlin.plugin.layout", "") == "LSP" -> KotlinPluginLayoutMode.LSP
            isRunningFromSources -> KotlinPluginLayoutMode.SOURCES
            else -> KotlinPluginLayoutMode.INTELLIJ
        }
    }

    @get:ApiStatus.Internal
    val kotlinPluginLayoutMode: KotlinPluginLayoutMode by lazyMode
}

@ApiStatus.Internal
enum class KotlinPluginLayoutMode {
    SOURCES,
    INTELLIJ,
    LSP,
}

object KotlinPluginLayout {

    /**
     * Directory with the bundled Kotlin compiler distribution. Includes the compiler itself and a set of compiler plugins
     * with a compatible version.
     */
    @JvmStatic
    val kotlincPath: Path
        get() = kotlincProvider.value

    @Suppress("IO_FILE_USAGE")
    /**
     * Directory with the bundled Kotlin compiler distribution. Includes the compiler itself and a set of compiler plugins
     * with a compatible version.
     */
    @Deprecated("Use kotlincPath instead", ReplaceWith("kotlincPath"))
    @JvmStatic
    val kotlinc: File
        get() = kotlincPath.toFile()

    /**
     * Location of the JPS plugin and all its dependency jars
     */
    val jpsPluginClasspathPath: List<Path>
        get() = jpsPluginClasspathProvider.value

    /**
     * Location of the JPS plugin and all its dependency jars
     */
    @Deprecated("Use jpsPluginClasspathPath instead", ReplaceWith("jpsPluginClasspathPath"))
    val jpsPluginClasspath: List<File>
        get() = jpsPluginClasspathPath.map(Path::toFile)

    val jsEngines: File? by lazy {
        kotlinc.resolve("lib").resolve("js.engines.jar").takeIf { it.exists() }
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

    private val kotlincProvider: Lazy<Path>
    private val jpsPluginClasspathProvider: Lazy<List<Path>>
    private val standaloneCompilerVersionProvider: Lazy<IdeKotlinVersion>

    init {
        val standaloneCompilerVersionDefaultProvider = lazy {
            val buildTxtPath = kotlincPath.resolve("build.txt")
            if (!buildTxtPath.exists()) {
                ideCompilerVersion
            } else {
                val rawVersion = buildTxtPath.readText().trim()
                IdeKotlinVersion.get(rawVersion)
            }
        }
        when (KotlinPluginLayoutModeProvider.kotlinPluginLayoutMode) {
            KotlinPluginLayoutMode.SOURCES -> {
                @Suppress("TestOnlyProblems")
                if (PluginManagerCore.isUnitTestMode) {
                    // When run on TC from the suite (junit 3+4), AppClassLoader contains only the bootstrap classpath.
                    // To ensure that the service loader becomes full path, let's use current class loader instead
                    val providerClass = TestKotlinArtifactsProvider::class.java
                    val provider =
                        ServiceLoader.load(providerClass, providerClass.classLoader).singleOrNull()
                        ?: error("TestKotlinArtifacts service provider is not found. Expected ...") // TODO
                    kotlincProvider = lazy {
                        // NOTE: FromKotlinDistForIdeByNameFallbackBundledFirCompilerPluginProvider
                        // requires it should be under KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX
                        provider.getKotlincCompilerCli()
                    }
                    jpsPluginClasspathProvider = lazy { provider.getJpsPluginClasspath() }
                    standaloneCompilerVersionProvider = standaloneCompilerVersionDefaultProvider
                }
                else {
                    val bundledJpsVersion by lazy {
                        KotlinMavenUtils.findLibraryVersion("kotlinc_kotlin_dist.xml")
                    }

                    kotlincProvider = lazy {
                        @Suppress("DEPRECATION")
                        val distJar = downloadArtifactForIdeFromSources(
                            OLD_KOTLIN_DIST_ARTIFACT_ID,
                            bundledJpsVersion
                        ) ?: error("Can't download dist")
                        val unpackedDistDir =
                            KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX_PATH.resolve("kotlinc-dist-for-ide-from-sources")
                        LazyZipUnpacker(unpackedDistDir.toFile()).lazyUnpack(distJar)
                    }

                    jpsPluginClasspathProvider = lazy {
                        @Suppress("DEPRECATION")
                        val jpsPluginArtifact = KotlinMavenUtils.findArtifactOrFail(
                            KOTLIN_MAVEN_GROUP_ID,
                            OLD_FAT_JAR_KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID,
                            bundledJpsVersion
                        )

                        listOf(jpsPluginArtifact)
                    }
                    standaloneCompilerVersionProvider = standaloneCompilerVersionDefaultProvider
                }
            }

            KotlinPluginLayoutMode.INTELLIJ -> {
                val kotlinPluginRoot = getPluginDistDirByClass(KotlinPluginLayout::class.java)
                    ?: error("Can't find jar file for ${KotlinPluginLayout::class.simpleName}")

                fun resolve(path: String) = kotlinPluginRoot.resolve(path).also { check(it.exists()) { "$it doesn't exist" } }

                kotlincProvider = lazy { resolve("kotlinc") }
                jpsPluginClasspathProvider = lazy { listOf(resolve("lib/jps/kotlin-jps-plugin.jar")) }
                standaloneCompilerVersionProvider = standaloneCompilerVersionDefaultProvider
            }

            KotlinPluginLayoutMode.LSP -> {
                kotlincProvider = lazy { error("LSP doesn't not include kotlinc") }
                jpsPluginClasspathProvider = lazy { error("LSP doesn't not include jps compiler") }
                standaloneCompilerVersionProvider = lazy { ideCompilerVersion /*there is no standalone compiler in LSP */}
            }
        }

        check(standaloneCompilerVersion.kotlinVersion <= ideCompilerVersion.kotlinVersion) {
            "standaloneCompilerVersion: $standaloneCompilerVersion, ideCompilerVersion: $ideCompilerVersion"
        }
    }
}

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class KotlinPluginLayoutService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): KotlinPluginLayoutService = project.service()
    }

    private val _remoteKotlincPath: AtomicReference<Path> = AtomicReference(null)

    fun getRemoteKotlincPath(): Path {
        _remoteKotlincPath.get()?.let { return it }

        val localKotlinc = KotlinPluginLayout.kotlincPath
        val eelDescriptor = project.getEelDescriptor()
        if (eelDescriptor == LocalEelDescriptor) return localKotlinc

        val parent = localKotlinc.parent

        val path = runBlockingMaybeCancellable { eelDescriptor.toEelApi().userInfo.home.resolve(".kotlin/kotlinc/") }

        val remote = transferLocalContentToRemote(
            parent,
            EelPathUtils.TransferTarget.Explicit(path.asNioPath())
        )

        val remoteKotlinc = remote.resolve(localKotlinc.name)

        _remoteKotlincPath.getAndSet(remoteKotlinc)?.let { return it }
        return remoteKotlinc
    }


    fun resolveRelativeToRemoteKotlinc(path: Path): Path {
        if (KotlinPluginLayoutModeProvider.kotlinPluginLayoutMode == KotlinPluginLayoutMode.LSP) {
            return path
        }

        val kotlinc = KotlinPluginLayout.kotlincPath
        if (!path.startsWith(kotlinc)) return path

        val relativize = kotlinc.relativize(path)

        val resolve = getRemoteKotlincPath().resolve(relativize)
        return resolve
    }
}