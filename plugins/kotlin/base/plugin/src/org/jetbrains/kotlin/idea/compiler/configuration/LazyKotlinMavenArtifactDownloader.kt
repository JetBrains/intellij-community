// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.kotlin.idea.base.plugin.artifacts.AbstractLazyFileOutputProducer
import org.jetbrains.kotlin.idea.compiler.configuration.LazyKotlinMavenArtifactDownloader.DownloadContext
import java.io.File
import java.security.MessageDigest

internal class LazyKotlinMavenArtifactDownloader(
    private val artifactId: String,
    private val version: String,
    private val artifactIsPom: Boolean = false,
) : AbstractLazyFileOutputProducer<Unit, DownloadContext>("${this::class.java.name}-$artifactId-$version") {

    override fun produceOutput(input: Unit, computationContext: DownloadContext): List<File> {
        computationContext.indicator.text = computationContext.indicatorDownloadText
        return KotlinArtifactsDownloader.downloadMavenArtifacts(
            artifactId,
            version,
            computationContext.project,
            computationContext.indicator,
            artifactIsPom,
            listOf()
        )
    }

    override fun updateMessageDigestWithInput(messageDigest: MessageDigest, input: Unit, buffer: ByteArray) {
        // The input is the Internet, we don't track it in this implementation
    }

    fun getDownloadedIfUpToDateOrEmpty() = getOutputIfUpToDateOrEmpty(Unit)
    fun lazyDownload(downloadContext: DownloadContext) = lazyProduceOutput(Unit, downloadContext)
    fun isUpToDate() = isUpToDate(Unit)

    data class DownloadContext(
        val project: Project,
        val indicator: ProgressIndicator,
        @NlsContexts.ProgressText val indicatorDownloadText: String,
    )
}
