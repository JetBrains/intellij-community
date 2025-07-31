// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradle.statistics

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.io.delete
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.gradle.diagnostic.KotlinGradleBuildErrorsChecker
import org.jetbrains.kotlin.idea.gradle.statistics.v2.flow.KotlinBuildToolFusFlowProcessor
import org.jetbrains.kotlin.statistics.BuildSessionLogger
import org.jetbrains.kotlin.statistics.fileloggers.MetricsContainer
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Delay between sequential checks of gradle statistics
 */
private const val EXECUTION_DELAY_MIN = 60L

/**
 * Delay between sequential checks of gradle build errors
 */
private const val ERRORS_EXECUTION_DELAY_MIN = 2L

/**
 * Maximum amount of directories which were reported as gradle user dirs
 * These directories should be monitored for reported gradle statistics.
 */
private const val MAXIMUM_USER_DIRS = 10

/**
 * Property name used for persisting gradle user dirs
 */
private const val GRADLE_USER_DIRS_PROPERTY_NAME = "kotlin-gradle-user-dirs"

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class KotlinGradleFUSLogger(private val project: Project, private val coroutineScope: CoroutineScope) {

    private val mutex: Mutex = Mutex()
    private val gradleErrorsChecker = KotlinGradleBuildErrorsChecker(project)

    companion object {
        private const val STATISTICS_FOLDER_NAME = "kotlin-profile"
        private const val MAX_FILE_AGE_DAYS = 31
        private val maxFileAge = MAX_FILE_AGE_DAYS.days.inWholeMilliseconds

        fun clearOldFiles(fusStatisticDir: Path) {
            fusStatisticDir.listDirectoryEntries()
                .filter { it.isRegularFile() }
                .filter {
                    val lastModified = it.getLastModifiedTime().toMillis()
                    (lastModified > 0) && (System.currentTimeMillis() - maxFileAge > lastModified)
                }.forEach { it.delete() }
        }
    }

    private suspend fun reportStatistics() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                for (gradleUserHome in gradleUserDirs) {
                    val fusStatisticDir = Path(gradleUserHome, STATISTICS_FOLDER_NAME)
                    if (fusStatisticDir.notExists()) continue

                    clearOldFiles(fusStatisticDir)
                    KotlinBuildToolFusFlowProcessor.process(fusStatisticDir)
                    KotlinGradleFUSLoggerProcessor.process(fusStatisticDir)
                }
            }
        }
    }

    private var gradleUserDirs: Set<String>
        set(value) {
            KotlinGradleFUSSettings.getInstance(project).gradleUserDirs = value
            // to clean up previous state
            obsoleteGradleUserDirs = null
        }
        get() {
            return KotlinGradleFUSSettings.getInstance(project).gradleUserDirs + (obsoleteGradleUserDirs ?: emptyList())
        }

    @Deprecated("to be dropped in 2024.3")
    private var obsoleteGradleUserDirs: List<String>?
        set(value) {
            PropertiesComponent.getInstance(project).setList(GRADLE_USER_DIRS_PROPERTY_NAME, value)
        }
        get() = PropertiesComponent.getInstance(project).getList(GRADLE_USER_DIRS_PROPERTY_NAME)

    fun populateGradleUserDir(path: String) {
        val currentState = gradleUserDirs

        if (path in currentState && obsoleteGradleUserDirs == null) return

        gradleUserDirs = (listOf(path) + currentState)
            .filter { Path(it).exists() }
            .take(MAXIMUM_USER_DIRS).toSet()
    }

    fun setup() {
        coroutineScope.launch {
            while (true) {
                delay(EXECUTION_DELAY_MIN.minutes)
                reportStatistics()
            }
        }

        gradleErrorsChecker.init()
        coroutineScope.launch {
            while (true) {
                delay(ERRORS_EXECUTION_DELAY_MIN.minutes)
                gradleErrorsChecker.run()
            }
        }
    }

    fun scheduleReportStatistics() {
        coroutineScope.launch { reportStatistics() }
    }
}

/**
 * Processor for Kotlin Gradle Feature Usage Statistics (FUS) data.
 * This object is responsible for reading and processing FUS metrics from files
 * generated during Kotlin Gradle builds.
 *
 * Expected file formats:
 * - Files are located in the "kotlin-profile" directory under the Gradle user home
 * - "*.profile" files contain metrics from Kotlin Gradle Plugin
 *
 * Expected file content:
 *  - Each file contains a list of "key=value" lines separated by newlines
 *  - BUILD_FINISHED separate build statistic for different builds in one file
 */

private object KotlinGradleFUSLoggerProcessor {
    fun process(statisticsFolder: Path) {
        for (statisticFile in BuildSessionLogger.listProfileFiles(statisticsFolder.toFile())) {
            var fileWasRead = true
            try {
                var previousEvent: MetricsContainer? = null
                fileWasRead = MetricsContainer.readFromFile(statisticFile) { metricContainer ->
                    KotlinGradleFUSCollector.reportMetrics(metricContainer, previousEvent)
                    previousEvent = metricContainer
                }
            } catch (e: Exception) {
                Logger.getInstance(KotlinGradleFUSCollector::class.java)
                    .info("Failed to process file ${statisticFile.absolutePath}: ${e.message}", e)
            } finally {
                if (fileWasRead && !statisticFile.delete()) {
                    Logger.getInstance(KotlinGradleFUSCollector::class.java)
                        .warn("[FUS] Failed to delete file ${statisticFile.absolutePath}")
                }
            }
        }
    }
}