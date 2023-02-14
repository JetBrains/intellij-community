// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import org.jetbrains.kotlin.idea.base.plugin.artifacts.*
import org.jetbrains.kotlin.idea.base.plugin.artifacts.*
import org.jetbrains.kotlin.idea.base.plugin.artifacts.AbstractLazyFileOutputProducer
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.LazyFileOutputProducer
import org.jetbrains.kotlin.idea.compiler.configuration.LazyKotlinMavenArtifactDownloader.DownloadContext
import java.io.File
import java.security.MessageDigest

/**
 * This class represents the pipeline:
 * ```
 *                                   Internet
 *                                      │
 *                     ┌────────────────┴────────────────┐
 *                     │                                 │
 *                     ▼                                 ▼
 *       "kotlin-dist-for-jps-meta" pom          all transitive jars
 *                                                 in maven repo
 *                                                       │
 *                                                       ▼
 *                                  jars are structured in "kotlinc dist layout"
 *                             (`./gradlew dist` in kotlin repo defines the layout)
 * ```
 * For each Kotlin version separate pipeline is created (`uniquePipelineId` in [AbstractLazyFileOutputProducer])
 *
 * It's important to declare the pom as an output (even thought it doesn't participate in further pipeline) because we want to re-calculate
 * the whole pipeline if the pom changes ([AbstractLazyFileOutputProducer] guarantees us that). It's convenient for the local testing
 * ("install to maven local -> test" development cycle)
 */
internal class LazyKotlincDistDownloaderAndUnpacker(version: String) : LazyFileOutputProducer<Unit, DownloadContext> {
    private val downloader = LazyKotlinMavenArtifactDownloader(KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID, version, artifactIsPom = true)

    private val distLayoutProducer = LazyDistDirLayoutProducer(version, KotlinArtifactsDownloader.getUnpackedKotlinDistPath(version))

    override fun isUpToDate(input: Unit): Boolean {
        val downloaded = downloader.getDownloadedIfUpToDateOrEmpty()
            .filter { it.extension == "jar" }
            .takeIf { it.isNotEmpty() }
            ?: return false
        return distLayoutProducer.isUpToDate(downloaded)
    }

    override fun lazyProduceOutput(input: Unit, computationContext: DownloadContext): List<File> {
        val downloaded = downloader.lazyDownload(computationContext)
            .filter { it.extension == "jar" }
            .takeIf { it.isNotEmpty() }
            ?: return emptyList()
        return listOf(distLayoutProducer.lazyProduceDist(downloaded))
    }

    fun lazyProduceDist(context: DownloadContext): File? = lazyProduceOutput(Unit, context).singleOrNull()
    fun isUpToDate() = isUpToDate(Unit)
}

private class LazyDistDirLayoutProducer(version: String, private val unpackedDistDestination: File) :
    AbstractLazyFileOutputProducer<List<File>, Unit>("${LazyDistDirLayoutProducer::class.java.name}-$version") {

    override fun produceOutput(input: List<File>, computationContext: Unit): List<File> { // inputs are jarsInMavenRepo
        unpackedDistDestination.deleteRecursively()
        val lib = unpackedDistDestination.resolve("lib")
        check(lib.mkdirs()) { "Can't create $lib directory" }
        for (jarInMavenRepo in input) {
            jarInMavenRepo.copyTo(lib.resolve(getDistJarNameFromMavenJar(jarInMavenRepo) ?: continue))
        }
        val jsEngines = KotlinPluginLayout.jsEngines
        jsEngines.copyTo(lib.resolve(jsEngines.name)) // js.engines is required to avoid runtime errors when compiling kts via JPS
        return listOf(unpackedDistDestination)
    }

    override fun updateMessageDigestWithInput(messageDigest: MessageDigest, input: List<File>, buffer: ByteArray) {
        messageDigest.update(input, buffer)
    }

    fun lazyProduceDist(jars: List<File>): File =
        lazyProduceOutput(jars, Unit).singleOrNull() ?: error("${LazyDistDirLayoutProducer::produceOutput.name} returns single element")

    /**
     * Renames arbitrary Kotlin Maven artifact jar to its name in kotlinc-dist (`./gradlew dist` in kotlin repo)
     */
    private fun getDistJarNameFromMavenJar(jarInMavenRepo: File): String? {
        val version = jarInMavenRepo.parentFile.name // jars in maven repo has version as their parent directory
        val nameWithoutExtension = jarInMavenRepo.nameWithoutExtension
        if (nameWithoutExtension.startsWith("jline") || nameWithoutExtension.startsWith("jansi")) {
            // org.jline.jline OR org.fusesource.jansi.jansi. Those artifacts are actually not presented in kotlin dist.
            // It's a mistake that those artifacts are mentioned as dependency for kotlin-daemon.
            // Those artifacts no longer mentioned as a dependency starting from kotlin 1.7.20
            return null
        }
        if (jarInMavenRepo.name == KotlinArtifactNames.JETBRAINS_ANNOTATIONS) {
            return jarInMavenRepo.name
        }
        if (nameWithoutExtension.startsWith("kotlin-maven-serialization")) {
            return "kotlinx-serialization-compiler-plugin.jar"
        }
        if (nameWithoutExtension.startsWith("kotlin-maven-sam-with-receiver") || nameWithoutExtension.startsWith("kotlin-maven-allopen") ||
            nameWithoutExtension.startsWith("kotlin-maven-lombok") || nameWithoutExtension.startsWith("kotlin-maven-noarg")
        ) {
            return nameWithoutExtension.removePrefix("kotlin-maven-").removeSuffix("-$version") + "-compiler-plugin.jar"
        }
        if (nameWithoutExtension.startsWith("kotlin-android-extensions-runtime")) {
            return "android-extensions-runtime.jar"
        }
        if (nameWithoutExtension.startsWith("kotlin-android-extensions")) {
            return "android-extensions-compiler.jar"
        }
        return nameWithoutExtension.removeSuffix("-$version") + ".jar"
    }
}
