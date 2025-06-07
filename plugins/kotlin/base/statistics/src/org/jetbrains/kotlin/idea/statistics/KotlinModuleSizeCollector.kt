// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.KotlinFileType
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

internal class KotlinModuleSizeCollector : ProjectUsagesCollector() {

    companion object {
        private val logger = Logger.getInstance(KotlinModuleSizeCollector::class.java)
        private const val AVERAGE_SIZE_PER_LINE = 40
    }

    override fun getGroup(): EventLogGroup = GROUP

    // Collector ID
    private val GROUP = EventLogGroup("kotlin.project.structure", 1)

    private fun IntRange.toBucketName(): String {
        if (endInclusive == Int.MAX_VALUE) return "bucket_${start}_max"
        return "bucket_${start}_${endInclusive}"
    }

    private fun List<IntRange>.toBuckets() = map { range ->
        StatisticsBucket(
            eventField = EventFields.Int(range.toBucketName()),
            range = range,
        )
    }

    // These are the ranges that define the buckets for line counts we use
    // Important: The ranges determine the names of the event's fields, so if the ranges are changed,
    // the version of the collector needs to be increased.
    private val lineCountBucketRanges = listOf(
        0..0,
        1..100,
        101..1000,
        1001..2000,
        2001..5000,
        5001..10000,
        10001..20000,
        20001..50000,
        50001..100000,
        100001..200000,
        200001..500000,
        500001..1000000,
        1000001..Int.MAX_VALUE
    )
    private val LINE_COUNT_BUCKETS = lineCountBucketRanges.toBuckets()
    private val LINE_COUNT_EVENT = GROUP.registerVarargEvent(
        eventId = "modules.sizes.line_count",
        *LINE_COUNT_BUCKETS.map { it.eventField }.toTypedArray(),
    )

    // See the comment about the line count ranges above!
    private val fileCountBucketRanges = listOf(
        0..0,
        1..1,
        2..4,
        5..7,
        8..15,
        16..25,
        26..40,
        41..70,
        71..100,
        101..300,
        301..600,
        601..1000,
        1001..1500,
        1501..2500,
        2501..4000,
        4001..Int.MAX_VALUE
    )

    private val FILE_COUNT_BUCKETS = fileCountBucketRanges.toBuckets()
    private val FILE_COUNT_EVENT = GROUP.registerVarargEvent(
        eventId = "modules.sizes.file_count",
        *FILE_COUNT_BUCKETS.map { it.eventField }.toTypedArray(),
    )

    private class StatisticsBucket(
        val eventField: EventField<Int>,
        val range: IntRange,
    )

    /**
     * This class is responsible for aggregating the data of each module for the entire project.
     * Each bucket defines a range and the count of modules whose values fall into the range.
     */
    private inner class ProjectStatistics(
        val fileCountBuckets: List<StatisticsBucket>,
        val lineCountBuckets: List<StatisticsBucket>
    ) {
        private inner class BucketCount(val bucket: StatisticsBucket, var count: Int)
        private val fileCountBucketCounts = fileCountBuckets.map { BucketCount(it, 0) }
        private val lineCountBucketCounts = lineCountBuckets.map { BucketCount(it, 0) }

        fun List<BucketCount>.increaseBucketCount(value: Int) {
            firstOrNull { it.bucket.range.contains(value) }?.count += 1
        }

        fun increaseFileCountBucket(fileCount: Int) {
            fileCountBucketCounts.increaseBucketCount(fileCount)
        }

        fun increaseLineCountBucket(totalFileSize: Int) {
            // To access the count of lines in a file, we would have to access the file (or load the PSI document).
            // However, we can cheaply access the size of each file, so we estimate the lines of code in a file
            // using some reasonable average size per line.
            val estimatedLinesOfCode = ceil(totalFileSize.toDouble() / AVERAGE_SIZE_PER_LINE).toInt()
            lineCountBucketCounts.increaseBucketCount(estimatedLinesOfCode)
        }

        fun generateEvents(): Set<MetricEvent> = setOf(
            LINE_COUNT_EVENT.metric(lineCountBucketCounts.map { it.bucket.eventField.with(it.count) }),
            FILE_COUNT_EVENT.metric(fileCountBucketCounts.map { it.bucket.eventField.with(it.count) })
        )
    }

    private class ModuleStatistics(
        var fileCount: Int = 0,
        var fileSize: Int = 0,
        var hasJavaFiles: Boolean = false, // this is not used yet, but we might use it in the future
    )

    // Gradle generates a lot of accessor files as .kt files that we want to ignore
    private fun VirtualFile.isGeneratedGradleFile(): Boolean {
        return path.contains("build/generated-sources/kotlin")
    }

    private suspend fun Project.gatherProjectStatistics(): Set<MetricEvent> {
        val projectStatistics = ProjectStatistics(
            fileCountBuckets = FILE_COUNT_BUCKETS,
            lineCountBuckets = LINE_COUNT_BUCKETS
        )

        for (module in modules) {
            val startTime = System.currentTimeMillis()
            val moduleStatistics = ModuleStatistics()

            readAction {
                val moduleFileIndex = ModuleRootManager.getInstance(module).fileIndex
                moduleFileIndex.iterateContent { file ->
                    if (file.extension == KotlinFileType.EXTENSION && !file.isGeneratedGradleFile()) {
                        moduleStatistics.fileCount++
                        moduleStatistics.fileSize += file.length.toInt()
                    } else if (file.extension == JavaFileType.DEFAULT_EXTENSION) {
                        moduleStatistics.hasJavaFiles = true
                    }
                    true
                }
            }

            projectStatistics.increaseLineCountBucket(moduleStatistics.fileSize)
            projectStatistics.increaseFileCountBucket(moduleStatistics.fileCount)
            logger.debug("Collecting stats for module ${module.name} took ${System.currentTimeMillis() - startTime} ms")
        }

        return projectStatistics.generateEvents()
    }

    override suspend fun collect(project: Project): Set<MetricEvent> {
        if (!KotlinModuleSizeCollectorTracker.getInstance(project).shouldCollectData()) return emptySet()

        val startTime = System.currentTimeMillis()
        val result = project.gatherProjectStatistics()
        KotlinModuleSizeCollectorTracker.getInstance(project).onCollectionFinished()
        logger.debug("Collecting stats for project took ${System.currentTimeMillis() - startTime} ms")

        return result
    }
}

internal class KotlinModuleSizeCollectorState : BaseState() {
    // Unix time seconds
    var lastProjectScan by property(0L)
}

/**
 * We only want to collect module statistics for Kotlin once every few weeks to not impact performance every time the project is opened.
 * This is why we use this storage to remember the last time we collected the data for the project.
 */
@Service(Service.Level.PROJECT)
@State(name = "Project", storages = [Storage(value = StoragePathMacros.CACHE_FILE)])
internal class KotlinModuleSizeCollectorTracker : PersistentStateComponent<KotlinModuleSizeCollectorState> {

    companion object {
        private val DURATION_BETWEEN_COLLECTION = Duration.ofDays(14)

        fun getInstance(project: Project): KotlinModuleSizeCollectorTracker {
            return project.service()
        }
    }

    internal var currentState: KotlinModuleSizeCollectorState = KotlinModuleSizeCollectorState()

    override fun getState(): KotlinModuleSizeCollectorState = currentState

    override fun loadState(state: KotlinModuleSizeCollectorState) {
        currentState = state
    }

    internal fun onCollectionFinished() {
        currentState.lastProjectScan = Instant.now().epochSecond
    }

    internal fun shouldCollectData(): Boolean {
        val lastProjectScan = state.lastProjectScan
        if (lastProjectScan == 0L) return true
        val lastCollection = Instant.ofEpochSecond(lastProjectScan)
        return Duration.between(lastCollection, Instant.now()) > DURATION_BETWEEN_COLLECTION
    }
}