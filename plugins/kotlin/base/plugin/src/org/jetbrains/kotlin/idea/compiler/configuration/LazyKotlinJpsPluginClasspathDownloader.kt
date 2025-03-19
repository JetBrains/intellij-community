// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.plugin.KotlinBasePluginBundle
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import org.jetbrains.kotlin.idea.base.plugin.artifacts.LazyFileOutputProducer
import org.jetbrains.kotlin.idea.compiler.configuration.LazyKotlinMavenArtifactDownloader.DownloadContext
import java.io.File

private val VERSION_UNTIL_OLD_FAT_JAR_IS_AVAILABLE = IdeKotlinVersion.get("1.7.20")

private val LOG = logger<LazyKotlinJpsPluginClasspathDownloader>()

class LazyKotlinJpsPluginClasspathDownloader(private val version: String) :
    LazyFileOutputProducer<Unit, LazyKotlinJpsPluginClasspathDownloader.Context> {

    private val newDownloader = LazyKotlinMavenArtifactDownloader(KotlinArtifactConstants.KOTLIN_JPS_PLUGIN_PLUGIN_ARTIFACT_ID, version)
    private val oldDownloader =
        if (IdeKotlinVersion.get(version) < VERSION_UNTIL_OLD_FAT_JAR_IS_AVAILABLE) {
            @Suppress("DEPRECATION")
            LazyKotlinMavenArtifactDownloader(KotlinArtifactConstants.OLD_FAT_JAR_KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID, version)
        } else {
            null
        }

    override fun isUpToDate(input: Unit) =
        if (IdeKotlinVersion.get(version).isStandaloneCompilerVersion) true
        else newDownloader.isUpToDate() || oldDownloader?.isUpToDate() == true

    override fun lazyProduceOutput(input: Unit, computationContext: Context): List<File> {
        getDownloadedIfUpToDateOrEmpty().takeIf { it.isNotEmpty() }?.let { return it }

        val downloadContext = DownloadContext(
            computationContext.project,
            computationContext.indicator,
            KotlinBasePluginBundle.message("progress.text.downloading.kotlin.jps.plugin")
        )
        return newDownloader.lazyDownload(downloadContext).takeIf { it.isNotEmpty() }
            ?: oldDownloader?.lazyDownload(downloadContext)
            ?: emptyList()
    }

    fun getDownloadedIfUpToDateOrEmpty(): List<File> {
        LOG.debug("Requested download for version $version")
        LOG.debug("Found standalone compiler version ${KotlinPluginLayout.standaloneCompilerVersion}")

        return if (IdeKotlinVersion.get(version).isStandaloneCompilerVersion) {
            LOG.debug("Using standalone compiler version ${KotlinPluginLayout.standaloneCompilerVersion}")
            KotlinPluginLayout.jpsPluginClasspath
        } else {
            LOG.debug("Starting download for version $version")
            newDownloader.getDownloadedIfUpToDateOrEmpty().takeIf { it.isNotEmpty() }
                ?: oldDownloader?.getDownloadedIfUpToDateOrEmpty()
                ?: emptyList()
        }
    }

    fun lazyDownload(computationContext: Context) = lazyProduceOutput(Unit, computationContext)

    data class Context(val project: Project, val indicator: ProgressIndicator)
}
