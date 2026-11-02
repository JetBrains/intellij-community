// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jps

import com.intellij.compiler.server.BuildProcessParametersProvider
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinBasePluginBundle
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.compiler.configuration.LazyKotlinJpsPluginClasspathDownloader
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.isDirectory

// Must match `JpsBtaToolchainLoader.IMPL_HOME_PROPERTY` in the Kotlin JPS plugin
private const val BTA_IMPL_HOME_PROPERTY = "kotlin.jps.build.tools.impl.home"

private val LOG = logger<KotlinJpsBuildProcessParametersProvider>()

/**
 * Provides the Kotlin JPS builder and its home to the build process.
 *
 * With the `kotlin.jps.use.build.tools.api` registry key, the build process gets the builder that the IDE bundles, see
 * [KotlinJpsBuilderClasspath], and the Build Tools API implementation of the project's Kotlin version. Without the
 * key, it gets the legacy JPS plugin of the project's Kotlin version.
 *
 * The provider fails the build if it cannot download what the version needs. The download usually happens in
 * [SetupKotlinJpsPluginBeforeCompileTask], but before-compile tasks do not run for "Build Project Automatically".
 */
class KotlinJpsBuildProcessParametersProvider(private val project: Project) : BuildProcessParametersProvider() {
    override fun getClassPath(): List<String> {
        val version = KotlinJpsPluginSettings.jpsVersion(project)

        if (KotlinArtifactsDownloader.isBuildToolsApiEnabled(version)) {
            return KotlinJpsBuilderClasspath.get()
        }

        val existingJpsPluginClasspath = LazyKotlinJpsPluginClasspathDownloader(version).getDownloadedIfUpToDateOrEmpty()
        val jpsPluginClassPath = existingJpsPluginClasspath.ifEmpty {
            // jps plugin of `version` is not yet downloaded

            downloadOrThrow(version)

            val jpsPluginClasspathAfterDownloading = LazyKotlinJpsPluginClasspathDownloader(version).getDownloadedIfUpToDateOrEmpty()
            if (jpsPluginClasspathAfterDownloading.isEmpty()) {
                error("Unable to download required Kotlin JPS plugin version $version: classpath is not available after downloading")
            }

            jpsPluginClasspathAfterDownloading
        }

        return jpsPluginClassPath.map { it.canonicalPath } + listOf(PathUtil.getJarPathForClass(PathUtil::class.java))
    }

    override fun getPathParameters(): List<Pair<String, Path>> {
        val version = KotlinJpsPluginSettings.jpsVersion(project)

        // The `KOTLIN_BUNDLED` path macro of the project model points to the dist of the project version, as in the IDE.
        // The macro expands to the standard library and to the compiler plugins, which must match the compiler.
        @Suppress("IO_FILE_USAGE")
        val kotlinDist = KotlinArtifactsDownloader.getUnpackedKotlinDistPath(project).toPath()

        if (!kotlinDist.isDirectory()) {
            // jps plugin dist of `version` is not yet downloaded
            downloadOrThrow(version)

            if (!kotlinDist.isDirectory()) {
                error("Unable to download required Kotlin JPS plugin version $version: dist is not available after downloading at $kotlinDist")
            }
        }

        if (!KotlinArtifactsDownloader.isBuildToolsApiEnabled(version)) {
            return listOf(Pair("-Djps.kotlin.home=", kotlinDist))
        }

        val implementation = KotlinArtifactsDownloader.getBuildToolsImplDirectory(version)
        if (!implementation.isDirectory()) {
            downloadOrThrow(version)

            if (!implementation.isDirectory()) {
                error("Unable to download the Kotlin Build Tools API implementation $version: it is not available after downloading at $implementation")
            }
        }
        LOG.debug { "Kotlin JPS gets the Build Tools API implementation from $implementation" }

        return listOf(
            Pair("-Djps.kotlin.home=", kotlinDist),
            Pair("-D$BTA_IMPL_HOME_PROPERTY=", implementation),
        )
    }

    override fun getVMArguments(): List<String> =
        if (Registry.`is`("kotlin.jps.instrument.bytecode", false))
            listOf("-Dkotlin.jps.instrument.bytecode=true")
        else
            emptyList()

    private fun downloadOrThrow(version: String) {
        val future = CompletableFuture<Unit>()

        val title = KotlinBasePluginBundle.message("progress.text.downloading.kotlin.jps.plugin")
        ProgressManager.getInstance().run(object: Task.Backgroundable(project, title, true, ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val errorMessageRef = AtomicReference<String>()
                    val success = KotlinArtifactsDownloader.lazyDownloadMissingJpsPluginDependencies(
                        project = project,
                        jpsVersion = version,
                        indicator = ProgressManager.getInstance().getProgressIndicator(),
                        onError = { errorMessageRef.set(it) }
                    )
                    if (!success) {
                        val errorMessage = errorMessageRef.get() ?: "unknown error"
                        error("Unable to download required Kotlin JPS plugin version $version: $errorMessage")
                    }

                    future.complete(Unit)
                }
                catch (t: Throwable) {
                    future.completeExceptionally(t)
                }
            }
        })

        try {
            future.get()
        } catch (_: CancellationException) {
            error("Unable to download required Kotlin JPS plugin version $version: the download was cancelled")
        } catch (e: ExecutionException) {
            val cause = e.cause
            error("Unable to download required Kotlin JPS plugin version $version: ${cause?.message ?: "unknown error"}")
        }
    }
}
