// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.io.DigestUtil
import org.jetbrains.kotlin.idea.artifacts.*
import java.io.File
import java.security.MessageDigest

/**
 * This class represents the pipeline:
 * ```
 *                                kotlin version
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
 * It's important to declare the pom as an output (even thought it doesn't participate in further pipeline) because we want to re-calculate
 * the whole pipeline if the pom changes ([AbstractLazyFileOutputProducer] guarantees us that). It's convenient for the local testing
 * ("install to maven local -> test" development cycle)
 */
class LazyKotlincDistDownloaderAndUnpacker(version: String) : LazyFileOutputProducer<String, LazyPomAndJarsDownloader.Context> {
    private val downloader = LazyPomAndJarsDownloader(version)
    private val distLayoutProducer = LazyDistDirLayoutProducer(version, KotlinArtifactsDownloader.getUnpackedKotlinDistPath(version))

    override fun isUpToDate(input: String): Boolean { // input is version
        val downloaded = downloader.getOutputIfUpToDateOrEmpty(input)
            .filter { it.extension == "jar" }
            .takeIf { it.isNotEmpty() }
            ?: return false
        return distLayoutProducer.isUpToDate(downloaded)
    }

    override fun lazyProduceOutput(input: String, computationContext: LazyPomAndJarsDownloader.Context): List<File> {
        val downloaded = downloader.lazyProduceOutput(input, computationContext)
            .filter { it.extension == "jar" }
            .takeIf { it.isNotEmpty() }
            ?: return emptyList()
        return listOf(distLayoutProducer.lazyProduceDist(downloaded))
    }

    fun lazyProduceDist(input: String, context: LazyPomAndJarsDownloader.Context): File? {
        return lazyProduceOutput(input, context).singleOrNull()
    }
}

class LazyPomAndJarsDownloader(version: String) :
    AbstractLazyFileOutputProducer<String, LazyPomAndJarsDownloader.Context>("${LazyPomAndJarsDownloader::class.java.name}-$version") {

    override fun produceOutput(input: String, computationContext: Context): List<File> { // input is a version
        computationContext.indicator.text = computationContext.indicatorDownloadText
        return KotlinArtifactsDownloader.downloadMavenArtifacts(
            KotlinArtifacts.KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID,
            version = input,
            computationContext.project,
            computationContext.indicator,
            artifactIsPom = true
        )
    }

    override fun updateMessageDigestWithInput(messageDigest: MessageDigest, input: String) {
        input.byteInputStream().use { DigestUtil.updateContentHash(messageDigest, it) }
    }

    data class Context(val project: Project, val indicator: ProgressIndicator, @NlsContexts.ProgressText val indicatorDownloadText: String)
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
        return listOf(unpackedDistDestination)
    }

    override fun updateMessageDigestWithInput(messageDigest: MessageDigest, input: List<File>) {
        messageDigest.update(input)
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
        return nameWithoutExtension.removeSuffix("-$version") + ".jar"
    }
}
