// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.OLD_FAT_JAR_KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID
import org.jetbrains.kotlin.idea.artifacts.LazyFileOutputProducer
import org.jetbrains.kotlin.idea.base.plugin.KotlinBasePluginBundle
import org.jetbrains.kotlin.idea.compiler.configuration.LazyKotlinMavenArtifactDownloader.DownloadContext
import java.io.File

private val VERSION_UNTIL_OLD_FAT_JAR_IS_AVAILABLE = IdeKotlinVersion.get("1.7.20")

class LazyKotlinJpsPluginClasspathDownloader(private val version: String) :
    LazyFileOutputProducer<Unit, LazyKotlinJpsPluginClasspathDownloader.Context> {

    private val newDownloader = LazyKotlinMavenArtifactDownloader(KotlinArtifacts.KOTLIN_JPS_PLUGIN_PLUGIN_ARTIFACT_ID, version)
    private val oldDownloader =
        if (IdeKotlinVersion.get(version) < VERSION_UNTIL_OLD_FAT_JAR_IS_AVAILABLE) {
            LazyKotlinMavenArtifactDownloader(OLD_FAT_JAR_KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID, version)
        } else {
            null
        }

    override fun isUpToDate(input: Unit) =
        if (IdeKotlinVersion.get(version).isStandaloneCompilerVersion) true
        else newDownloader.isUpToDate() || oldDownloader?.isUpToDate() == true

    override fun lazyProduceOutput(input: Unit, computationContext: Context): List<File> {
        if (IdeKotlinVersion.get(version).isStandaloneCompilerVersion) {
            return KotlinPluginLayout.instance.jpsPluginClasspath
        }
        oldDownloader?.getDownloadedIfUpToDateOrEmpty()?.takeIf { it.isNotEmpty() }?.let { return it }

        val downloadContext = DownloadContext(
            computationContext.project,
            computationContext.indicator,
            KotlinBasePluginBundle.message("progress.text.downloading.kotlin.jps.plugin")
        )
        return newDownloader.lazyDownload(downloadContext).takeIf { it.isNotEmpty() }
            ?: oldDownloader?.lazyDownload(downloadContext)
            ?: emptyList()
    }

    fun getDownloadedIfUpToDateOrEmpty() =
        if (IdeKotlinVersion.get(version).isStandaloneCompilerVersion) {
            KotlinPluginLayout.instance.jpsPluginClasspath
        } else {
            newDownloader.getDownloadedIfUpToDateOrEmpty().takeIf { it.isNotEmpty() }
                ?: oldDownloader?.getDownloadedIfUpToDateOrEmpty()
                ?: emptyList()
        }

    fun lazyDownload(computationContext: Context) = lazyProduceOutput(Unit, computationContext)

    data class Context(val project: Project, val indicator: ProgressIndicator)
}
