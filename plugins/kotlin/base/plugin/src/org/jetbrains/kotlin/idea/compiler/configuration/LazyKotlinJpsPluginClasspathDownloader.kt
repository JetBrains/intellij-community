// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID
import org.jetbrains.kotlin.idea.artifacts.LazyFileOutputProducer
import org.jetbrains.kotlin.idea.base.plugin.KotlinBasePluginBundle
import org.jetbrains.kotlin.idea.compiler.configuration.LazyKotlinJpsPluginClasspathDownloader.Context
import java.io.File

class LazyKotlinJpsPluginClasspathDownloader(version: String) : LazyFileOutputProducer<Unit, Context> {
    private val downloader = LazyKotlinMavenArtifactDownloader(KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID, version)

    override fun isUpToDate(input: Unit) = downloader.isUpToDate()

    override fun lazyProduceOutput(input: Unit, computationContext: Context): List<File> {
        val context = LazyKotlinMavenArtifactDownloader.DownloadContext(
            computationContext.project,
            computationContext.indicator,
            KotlinBasePluginBundle.message("progress.text.downloading.kotlin.jps.plugin")
        )
        return downloader.lazyDownload(context)
    }

    fun getDownloadedIfUpToDateOrEmpty() = downloader.getOutputIfUpToDateOrEmpty()
    fun lazyDownload(computationContext: Context) = lazyProduceOutput(Unit, computationContext)

    data class Context(val project: Project, val indicator: ProgressIndicator)
}
