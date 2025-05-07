// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

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

    companion object {
        /**
         * Bump when you change the algorithm of how LazyDistDirLayoutProducer works.
         * It helps to make sure that we rebuild dist when the algorithm changes.
         */
        private const val ALGORITHM_VERSION = 1
    }

    private val kotlinVersion = IdeKotlinVersion.parse(version).getOrThrow()

    override fun produceOutput(input: List<File>, computationContext: Unit): List<File> { // inputs are jarsInMavenRepo
        check(unpackedDistDestination.deleteRecursively()) { "Can't delete $unpackedDistDestination" }
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
        messageDigest.update(ALGORITHM_VERSION.toBigInteger().toByteArray())
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

        // Starting Kotlin 1.9.0 release compiler plugins were decoupled from related maven plugin and added into kotlinc dist
        // Related issues:
        // - https://youtrack.jetbrains.com/issue/KT-52811
        // - https://youtrack.jetbrains.com/issue/KTI-38
        if (kotlinVersion <= IdeKotlinVersion.parse("1.8.255").getOrThrow()) {
            if (nameWithoutExtension.startsWith("kotlin-maven-serialization")) {
                return "kotlinx-serialization-compiler-plugin.jar"
            }
            if (nameWithoutExtension.startsWith("kotlin-maven-sam-with-receiver") || nameWithoutExtension.startsWith("kotlin-maven-allopen") ||
                nameWithoutExtension.startsWith("kotlin-maven-lombok") || nameWithoutExtension.startsWith("kotlin-maven-noarg")
            ) {
                return nameWithoutExtension.removePrefix("kotlin-maven-").removeSuffix("-$version") + "-compiler-plugin.jar"
            }
        } else {
            // These plugins are still present in dist pom for backward compatibility with older Kotlin Plugin releases
            val compatMavenPlugins = listOf(
                "kotlin-maven-allopen",
                "kotlin-maven-lombok",
                "kotlin-maven-noarg",
                "kotlin-maven-sam-with-receiver",
            )
            if (compatMavenPlugins.any { nameWithoutExtension.startsWith(it) }) return null

            // Keeping for compatibility until we will switch plugin to use new serialization compiler plugin jar
            if (nameWithoutExtension.startsWith("kotlin-maven-serialization")) {
                return "kotlinx-serialization-compiler-plugin.jar"
            }

            // 'kotlin-serialization-compiler-plugin' is not in the list by design
            // as we want to migrate dist to have all compiler plugins with 'kotlin-' prefix
            val compilerPluginNames = listOf(
                "kotlin-sam-with-receiver-compiler-plugin",
                "kotlin-allopen-compiler-plugin",
                "kotlin-lombok-compiler-plugin",
                "kotlin-noarg-compiler-plugin",
                "kotlin-assignment-compiler-plugin",
            )

            if (compilerPluginNames.any { nameWithoutExtension.startsWith(it) }) {
                return nameWithoutExtension.removePrefix("kotlin-").removeSuffix("-$version") + ".jar"
            }

        }

        return nameWithoutExtension.removeSuffix("-$version") + ".jar"
    }
}
