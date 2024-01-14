// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradle.statistics

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.gradle.diagnostic.KotlinGradleBuildErrorsChecker
import org.jetbrains.kotlin.statistics.BuildSessionLogger
import org.jetbrains.kotlin.statistics.BuildSessionLogger.Companion.STATISTICS_FOLDER_NAME
import org.jetbrains.kotlin.statistics.fileloggers.MetricsContainer
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.minutes

internal class KotlinGradleFUSLoggingActivity : ProjectActivity {
    init {
        val app = ApplicationManager.getApplication()
        if (app.isUnitTestMode || app.isHeadlessEnvironment) {
            throw ExtensionNotApplicableException.create()
        }
    }

    override suspend fun execute(project: Project) {
        project.service<KotlinGradleFUSLogger>().setup()
    }
}

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

    private suspend fun reportStatistics() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                for (gradleUserHome in gradleUserDirs) {
                    for (statisticFile in BuildSessionLogger.listProfileFiles(File(gradleUserHome, STATISTICS_FOLDER_NAME))) {
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
        }
    }

    private var gradleUserDirs: List<String>
        set(value) {
            PropertiesComponent.getInstance(project).setList(GRADLE_USER_DIRS_PROPERTY_NAME, value)
        }
        get() = PropertiesComponent.getInstance(project).getList(GRADLE_USER_DIRS_PROPERTY_NAME) ?: emptyList()

    fun populateGradleUserDir(path: String) {
        val currentState = gradleUserDirs
        if (path in currentState) return

        val result = ArrayList<String>()
        result.add(path)
        result.addAll(currentState)

        gradleUserDirs = result.filter { filePath -> Path(filePath).exists() }.take(MAXIMUM_USER_DIRS)
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