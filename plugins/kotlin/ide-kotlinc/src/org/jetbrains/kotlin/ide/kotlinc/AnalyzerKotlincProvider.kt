// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.ide.kotlinc

import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.BazelEnvironmentUtil
import com.intellij.util.io.Decompressor
import com.intellij.util.net.NetUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.ide.kotlinc.testFramework.AnalyzerKotlincTestHomeProvider
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.ServiceLoader
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.outputStream

/** Finds or downloads the kotlinc of the analyzer version. */
object AnalyzerKotlincProvider {
    private const val KOTLINC_DIR_NAME = "kotlinc"
    private const val KOTLIN_DIST_FOR_IDE = "kotlin-dist-for-ide"
    private const val KOTLIN_MAVEN_GROUP_PATH = "org/jetbrains/kotlin"
    private const val KOTLIN_SNAPSHOT_DIR_PATH = "lib/kotlin-snapshot"
    private const val BUILD_TXT = "build.txt"
    private const val DIST_SOURCE_TXT = "dist-source.txt"

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 60_000

    private val LOG = logger<AnalyzerKotlincProvider>()
    private val kotlinDistForIdeJar = "$KOTLIN_DIST_FOR_IDE-${KotlinCompilerVersion.VERSION}.jar"

    @Volatile
    private var kotlincHome: Path? = null
    private val mutex = Mutex()

    /** Downloads the kotlinc under the progress of the caller, if necessary. */
    suspend fun getKotlincHome(): Path = getKotlincHome(callerIndicator = null)

    /** Shows a modal progress on the EDT, and uses the progress indicator of the caller elsewhere. */
    fun getKotlincHomeBlocking(): Path {
        kotlincHome?.let { return it }
        if (ApplicationManager.getApplication()?.isDispatchThread == true) {
            // Not `runWithModalProgressBlocking`, because it fails under a read lock.
            return ProgressManager.getInstance().runProcessWithProgressSynchronously(
                ThrowableComputable { getKotlincHomeUnderCallerIndicator() },
                IdeKotlincBundle.message("progress.title.preparing.kotlin.compiler"),
                true,
                null,
            )
        }
        return getKotlincHomeUnderCallerIndicator()
    }

    private fun getKotlincHomeUnderCallerIndicator(): Path {
        val callerIndicator = ProgressManager.getGlobalProgressIndicator()
        return runBlockingMaybeCancellable { getKotlincHome(callerIndicator) }
    }

    private suspend fun getKotlincHome(callerIndicator: ProgressIndicator?): Path {
        kotlincHome?.let { return it }
        return mutex.withLock {
            kotlincHome ?: withContext(Dispatchers.IO) {
                // runBlockingMaybeCancellable does not pass the text and the fraction to the caller indicator.
                if (callerIndicator != null) findOrUnpack(callerIndicator)
                else coroutineToIndicator { findOrUnpack(ProgressManager.getGlobalProgressIndicator()) }
            }.also {
                LOG.info("The IDE kotlinc home is $it")
                kotlincHome = it
            }
        }
    }

    private fun findOrUnpack(indicator: ProgressIndicator?): Path = testKotlincHome() ?: bundledKotlinc() ?: unpackKotlinDistForIde(indicator)
    ?: error("Kotlin compiler distribution is not found for ${KotlinCompilerVersion.VERSION}")

    private fun testKotlincHome(): Path? {
        if (ApplicationManager.getApplication()?.isUnitTestMode != true) return null
        val providerClass = AnalyzerKotlincTestHomeProvider::class.java
        return ServiceLoader.load(providerClass, providerClass.classLoader).firstOrNull()?.kotlincHome()
    }

    private fun bundledKotlinc(): Path? {
        val kotlinPluginJar = PathManager.getJarForClass(AnalyzerKotlincProvider::class.java) ?: return null
        return bundledKotlinc(kotlinPluginJar)
    }

    // The path walk of `com.intellij.ide.plugins.getPluginDistDirByClass`, without its class loader lookup.
    internal fun bundledKotlinc(pluginJar: Path): Path? {
        val pluginRoot = pluginJar
            .parent
            .let { if (it.name == "modules") it.parent else it }
            .takeIf { it.name == "lib" }?.parent ?: return null
        val bundled = pluginRoot.resolve(KOTLINC_DIR_NAME).takeIf(::isKotlincHome) ?: return null
        val build = buildNumber(bundled)
        if (build?.startsWith(KotlinCompilerVersion.VERSION) != true) {
            LOG.info("The bundled kotlinc is $build, the IDE needs ${KotlinCompilerVersion.VERSION}")
            return null
        }
        return bundled
    }

    private fun unpackKotlinDistForIde(indicator: ProgressIndicator?): Path? {
        val target = PathManager.getSystemDir()
            .resolve(KOTLIN_DIST_FOR_IDE)
            .resolve(KotlinCompilerVersion.VERSION)

        // Only a snapshot dist can change under the same version. The stamp comes last, so an interrupted unpack has none.
        val snapshotJar = snapshotKotlinDistForIdeJar()
        if (snapshotJar == null && isKotlincHome(target) && target.resolve(DIST_SOURCE_TXT).exists()) return target

        val distJar = snapshotJar ?: downloadKotlinDistForIdeJar(indicator) ?: return null
        LOG.info("The IDE kotlinc dist comes from $distJar")
        return unpackKotlinDistForIde(distJar, target, indicator)
    }

    /** Keeps [target] when the same [distJar] unpacked it. */
    internal fun unpackKotlinDistForIde(distJar: Path, target: Path, indicator: ProgressIndicator? = null): Path {
        val distSource = target.resolve(DIST_SOURCE_TXT)
        val distStamp = Files.getLastModifiedTime(distJar).toMillis().toString()
        if (!isKotlincHome(target) || runCatching { Files.readString(distSource) }.getOrNull() != distStamp) {
            LOG.info("Unpacking the IDE kotlinc dist into $target")
            indicator?.text = IdeKotlincBundle.message("progress.text.unpacking.kotlinc.dist")
            NioFiles.deleteRecursively(target)
            Files.createDirectories(target)
            Decompressor.Zip(distJar).overwrite(true).extract(target)
            Files.writeString(distSource, distStamp)
        }

        check(isKotlincHome(target)) { "Kotlin compiler distribution is incomplete: $target" }
        return target
    }

    private fun snapshotKotlinDistForIdeJar(): Path? {
        return Path.of(PathManager.getCommunityHomePath())
            .resolve(KOTLIN_SNAPSHOT_DIR_PATH)
            .resolve(KOTLIN_MAVEN_GROUP_PATH)
            .resolve(KOTLIN_DIST_FOR_IDE)
            .resolve(KotlinCompilerVersion.VERSION)
            .resolve(kotlinDistForIdeJar)
            .takeIf { it.isRegularFile() }
    }

    private fun isKotlincHome(path: Path): Boolean {
        return path.resolve(BUILD_TXT).exists() && path.resolve("lib").exists()
    }

    private fun buildNumber(kotlincHome: Path): String? =
        runCatching { Files.readString(kotlincHome.resolve(BUILD_TXT)).trim() }.getOrNull()

    private fun downloadKotlinDistForIdeJar(indicator: ProgressIndicator?): Path? {
        val target = PathManager.getSystemDir()
            .resolve(KOTLIN_DIST_FOR_IDE)
            .resolve("downloads")
            .resolve(kotlinDistForIdeJar)
        if (target.exists()) return target

        Files.createDirectories(target.parent)
        // Another IDE can download into the same directory.
        val temp = Files.createTempFile(target.parent, target.name, ".tmp")
        try {
            for (url in kotlinDistForIdeUrls) {
                try {
                    downloadTo(url, temp, indicator)
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
                    return target
                } catch (e: IOException) {
                    LOG.info("The IDE kotlinc dist download failed: $url", e)
                }
            }
            return null
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun downloadTo(url: String, target: Path, indicator: ProgressIndicator?) {
        indicator?.text = IdeKotlincBundle.message("progress.text.downloading.kotlinc.dist")
        val connection = URI(url).toURL().openConnection()
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        try {
            connection.getInputStream().use { input ->
                target.outputStream().use { output ->
                    NetUtils.copyStreamContent(
                        indicator,
                        input,
                        output,
                        connection.contentLengthLong,
                        true,
                    )
                }
            }
        } finally {
            (connection as? HttpURLConnection)?.disconnect()
        }
    }

    // A copy of the repositories in `KotlinArtifactsDownloader`, because the KotlinScripting plugin does not depend on the Kotlin plugin.
    private val kotlinDistForIdeUrls: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val artifactPath = "$KOTLIN_MAVEN_GROUP_PATH/$KOTLIN_DIST_FOR_IDE/${KotlinCompilerVersion.VERSION}/$kotlinDistForIdeJar"
        buildList {
            add("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies")
            add("https://cache-redirector.jetbrains.com/intellij-dependencies")
            add("https://cache-redirector.jetbrains.com/repo1.maven.org/maven2")

            val isTestLikeRun = ApplicationManager.getApplication().isUnitTestMode
                    || AppMode.isRunningFromDevBuild()
                    || BazelEnvironmentUtil.isBazelTestRun()

            if (isTestLikeRun) {
                add("https://packages.jetbrains.team/maven/p/kt/experimental")
            }
        }.map { "$it/$artifactPath" }
    }
}
