// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.statistics.v2.flow

import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Processor for Kotlin build tool Feature Usage Statistics (FUS) data.
 * This object is responsible for reading, aggregating, and processing FUS metrics from files
 * generated during Kotlin builds.
 *
 * Expected file formats:
 * - Files are located in the "kotlin-profile" directory under the Gradle user home
 * - Files contains buildId in the filename
 * - `*.finish-profile` files marker that build is finished and metrics can be processed
 * - `*.plugin-profile` files contain metrics from FUS Gradle Plugin
 * - `*.kotlin-profile` files contain metrics from Kotlin Gradle Plugin
 *
 * Expected file content:
 * - Each file name starts with build
 * - Each file contains a list of "key=value" lines separated by newlines
 */
object KotlinBuildToolFusFlowProcessor {
    private const val FILE_NAME_BUILD_ID_PREFIX_SEPARATOR = "."
    private val fusFileExtensionsGlob = listOf("plugin-profile", "kotlin-profile").joinToString(
        prefix = "*.{",
        separator = ",",
        postfix = "}"
    )
    private const val BUILD_FINISHED_FUS_FILE_EXTENSION = "finish-profile"

    internal fun process(statisticsFolder: Path) {
        try {
            val pathsByBuildId = filterFilesToRead(statisticsFolder)
            pathsByBuildId.forEach { (buildId, paths) ->
                val aggregatedFusMetric = aggregateMetricsForBuildId(buildId, paths) ?: return@forEach

                kotlinBuildToolsFusEvenList.forEach {
                    KotlinBuildToolFusFlowCollector.send(it, buildId, aggregatedFusMetric)
                }

                removeAllFusReportsForBuild(statisticsFolder, buildId)

            }
        } catch (e: Exception) {
            Logger.getInstance(KotlinBuildToolFusFlowProcessor::class.java)
                .warn("Failed to process $statisticsFolder directory: ${e.message}", e)
        }
    }

    fun filterFilesToRead(staticFolder: Path): Map<String, List<Path>> {
        val buildFinishFiles = staticFolder.listDirectoryEntries("*.$BUILD_FINISHED_FUS_FILE_EXTENSION")
        return buildFinishFiles.associate { buildFinishFile ->
            val buildId = buildFinishFile.fileName.toString().substringBefore(".$BUILD_FINISHED_FUS_FILE_EXTENSION")
            val fusFiles = staticFolder.listDirectoryEntries(
                fusFileExtensionsGlob
            ).filter { it.isRegularFile() }.filter { it.fileName.toString().startsWith(buildId) }
            buildId to (fusFiles + listOf(buildFinishFile))
        }
    }

    /**
     * Deletes all Feature Usage Statistics (FUS) report files for a specific build.
     *
     * This method removes all files in the statistics folder that start with the specified build ID.
     * So reports for both new and old reporting flows will be deleted to avoid duplications.
     *
     * @param staticFolder The path to the Fus report directory
     * @param buildId The ID of the build whose FUS report files should be deleted
     */
    private fun removeAllFusReportsForBuild(staticFolder: Path, buildId: String) {
        staticFolder.listDirectoryEntries().filter { it.name.startsWith(buildId) }.forEach {
            Files.deleteIfExists(it)
        }
    }

    fun aggregateMetricsForBuildId(buildId: String, files: List<Path>): Set<AggregatedFusMetric<*>>? =
        files.mapNotNull {
            val rawMetricsForBuild = readFile(it)
            if (rawMetricsForBuild.buildId != buildId) {
                Logger.getInstance(KotlinBuildToolFusFlowProcessor::class.java)
                    .warn("File ${it.fileName} contains data for buildId `${rawMetricsForBuild.buildId}`, but build `$buildId` was expected. Skipping file.")
                return@mapNotNull null
            }
            rawMetricsForBuild
        }.reduceOrNull { rawMetricsForBuild1, rawMetricsForBuild2 ->
            rawMetricsForBuild1.add(rawMetricsForBuild2)
        }?.aggregateMetrics()


    private fun readFile(
        path: Path,
    ): RawMetricsForBuild {
        var buildId = path.name.substringBefore(FILE_NAME_BUILD_ID_PREFIX_SEPARATOR)
        val rawMetrics = HashMap<KotlinBuildToolFusMetric<*>, MutableList<RawFusValue>>()
        path.toFile().forEachLine { lines ->
            when {
                lines.startsWith("BUILD FINISHED") -> return@forEachLine
                lines.startsWith("Build:") -> buildId = lines.substringAfter("Build:").trim().also {
                    if (buildId != it) {
                        Logger.getInstance(KotlinBuildToolFusFlowProcessor::class.java)
                            .warn("Kotlin build FUS file with name ${path.name} contains data for build $it")
                    }
                }

                else -> {
                    val metric = lines.split("=").filter { it.isNotBlank() }
                    if (metric.size == 2) {
                        kotlinFusMetricsMap[metric[0]]?.metric?.let { metricKey ->
                            rawMetrics.getOrPut(metricKey) { mutableListOf() }.add(RawFusValue(metric[1]))
                        }
                    }
                }
            }
        }
        return RawMetricsForBuild(buildId, rawMetrics)
    }

    private fun RawMetricsForBuild.aggregateMetrics(): Set<AggregatedFusMetric<*>> {
        return rawMetrics.mapNotNull { it.key.process(it.value) }.toSet()
    }

    private fun RawMetricsForBuild.add(rawMetricsForBuild: RawMetricsForBuild): RawMetricsForBuild {
        if (rawMetricsForBuild.buildId != this.buildId) {
            Logger.getInstance(KotlinBuildToolFusFlowProcessor::class.java)
                .warn("Try to aggregate metrics for different builds: ${this.buildId} and ${rawMetricsForBuild.buildId}")
            return this
        }

        rawMetricsForBuild.rawMetrics.forEach { (metric, rawValues) ->
            this.rawMetrics.getOrPut(metric) { mutableListOf() }.addAll(rawValues)
        }
        return this
    }
}

private typealias RawMetricsList = MutableList<RawFusValue>

private data class RawMetricsForBuild(
    val buildId: String,
    val rawMetrics: MutableMap<KotlinBuildToolFusMetric<*>, RawMetricsList>,
)

@JvmInline
value class RawFusValue(val value: String)