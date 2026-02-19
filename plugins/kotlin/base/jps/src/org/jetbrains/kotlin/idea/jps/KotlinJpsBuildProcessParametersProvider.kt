// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jps

import com.intellij.compiler.server.BuildProcessParametersProvider
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

/**
 * Provide a correct JPS plugin classpath and home for JPS plugin specified in options.
 * Fails the build if it's not possible to download a correct version.
 * JPS plugin is usually downloaded in [org.jetbrains.kotlin.idea.jps.SetupKotlinJpsPluginBeforeCompileTask],
 * but before compile tasks do not work for "Build Project Automatically" +
 * we'd really like to fail the build if it's not possible to download the correct version of JPS plugin
 */
class KotlinJpsBuildProcessParametersProvider(private val project: Project) : BuildProcessParametersProvider() {
    override fun getClassPath(): List<String> {
        val version = KotlinJpsPluginSettings.jpsVersion(project)

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

        return jpsPluginClassPath.map { it.canonicalPath } + listOf(PathUtil.getJarPathForClass(com.intellij.util.PathUtil::class.java))
    }

    override fun getPathParameters(): List<Pair<String, Path>> {
        val version = KotlinJpsPluginSettings.jpsVersion(project)
        val kotlinDist = KotlinArtifactsDownloader.getUnpackedKotlinDistPath(project)
        if (!kotlinDist.isDirectory) {
            // jps plugin dist of `version` is not yet downloaded
            downloadOrThrow(version)

            if (!kotlinDist.isDirectory) {
                error("Unable to download required Kotlin JPS plugin version $version: dist is not available after downloading at $kotlinDist")
            }
        }

        return listOf(Pair("-Djps.kotlin.home=", kotlinDist.toPath()))
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

        future.get()
    }
}
