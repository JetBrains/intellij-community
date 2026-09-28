// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.eel.fs.EelFileUtils
import org.jetbrains.kotlin.idea.base.plugin.artifacts.AbstractLazyFileOutputProducer
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.KOTLIN_BUILD_TOOLS_IMPL_ARTIFACT_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.update
import org.jetbrains.kotlin.idea.compiler.configuration.LazyKotlinMavenArtifactDownloader.DownloadContext
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Downloads the Build Tools API implementation and its runtime dependencies into one directory. */
internal class LazyKotlinBuildToolsImplProducer(version: String) {
    private val artifactVersion = IdeKotlinVersion.get(version).artifactVersion

    private val downloader = LazyKotlinMavenArtifactDownloader(KOTLIN_BUILD_TOOLS_IMPL_ARTIFACT_ID, artifactVersion)

    private val layoutProducer = LazyBuildToolsImplDirLayoutProducer(
        artifactVersion,
        KotlinArtifactsDownloader.getBuildToolsImplDirectory(version),
    )

    fun lazyProduceDirectory(context: DownloadContext): Path? =
        layoutProducer.lazyProduceDirectory(downloader.lazyDownload(context))
}

@Suppress("IO_FILE_USAGE") // The artifact pipeline uses the File-based AbstractLazyFileOutputProducer API
private class LazyBuildToolsImplDirLayoutProducer(version: String, private val destination: Path) :
    AbstractLazyFileOutputProducer<List<java.io.File>, Unit>("${LazyBuildToolsImplDirLayoutProducer::class.java.name}-$version") {

    companion object {
        private const val ALGORITHM_VERSION = 1
    }

    override fun produceOutput(input: List<java.io.File>, computationContext: Unit): List<java.io.File> {
        EelFileUtils.deleteRecursively(destination)
        NioFiles.createDirectories(destination)
        // JPS loads all jars from this directory, so keep their Maven file names
        for (jarInMavenRepo in input) {
            Files.copy(jarInMavenRepo.toPath(), destination.resolve(jarInMavenRepo.name))
        }
        return listOf(destination.toFile())
    }

    override fun updateMessageDigestWithInput(messageDigest: MessageDigest, input: List<java.io.File>, buffer: ByteArray) {
        messageDigest.update(ALGORITHM_VERSION.toBigInteger().toByteArray())
        messageDigest.update(input, buffer)
    }

    fun lazyProduceDirectory(downloaded: List<java.io.File>): Path? {
        val jars = downloaded.filter { it.extension == "jar" }.ifEmpty { return null }
        return lazyProduceOutput(jars, Unit).singleOrNull()?.toPath()
            ?: error("${LazyBuildToolsImplDirLayoutProducer::produceOutput.name} returns single element")
    }
}
