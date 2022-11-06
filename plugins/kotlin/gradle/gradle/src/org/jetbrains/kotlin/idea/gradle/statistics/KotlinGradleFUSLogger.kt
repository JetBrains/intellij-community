// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradle.statistics

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.statistics.BuildSessionLogger
import org.jetbrains.kotlin.statistics.BuildSessionLogger.Companion.STATISTICS_FOLDER_NAME
import org.jetbrains.kotlin.statistics.fileloggers.MetricsContainer
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.Path
import kotlin.io.path.exists

class KotlinGradleFUSLogger : StartupActivity.DumbAware, Runnable {

    override fun runActivity(project: Project) {
        AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay(this, EXECUTION_DELAY_MIN, EXECUTION_DELAY_MIN, TimeUnit.MINUTES)
    }

    override fun run() {
        reportStatistics()
    }

    companion object {

        /**
         * Maximum amount of directories which were reported as gradle user dirs
         * These directories should be monitored for reported gradle statistics.
         */
        private const val MAXIMUM_USER_DIRS = 10

        /**
         * Delay between sequential checks of gradle statistics
         */
        const val EXECUTION_DELAY_MIN = 60L

        /**
         * Property name used for persisting gradle user dirs
         */
        private const val GRADLE_USER_DIRS_PROPERTY_NAME = "kotlin-gradle-user-dirs"

        private val isRunning = AtomicBoolean(false)

        fun reportStatistics() {
            if (isRunning.compareAndSet(false, true)) {
                try {
                    for (gradleUserHome in gradleUserDirs) {
                        BuildSessionLogger.listProfileFiles(File(gradleUserHome, STATISTICS_FOLDER_NAME))?.forEach { statisticFile ->
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
                } finally {
                    isRunning.set(false)
                }
            }
        }

        private var gradleUserDirs: List<String>
            set(value) = PropertiesComponent.getInstance().setList(
                GRADLE_USER_DIRS_PROPERTY_NAME, value
            )
            get() = PropertiesComponent.getInstance().getList(GRADLE_USER_DIRS_PROPERTY_NAME) ?: emptyList()

        fun populateGradleUserDir(path: String) {
            val currentState = gradleUserDirs
            if (path in currentState) return

            val result = ArrayList<String>()
            result.add(path)
            result.addAll(currentState)

            gradleUserDirs = result.filter { filePath -> Path(filePath).exists() }.take(MAXIMUM_USER_DIRS)
        }
    }
}
