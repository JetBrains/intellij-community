// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradle.statistics

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.addOptionTag
import com.intellij.util.xmlb.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jdom.Element
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

@Service(Service.Level.PROJECT)
@State(name = "KotlinGradleFUSSettings", storages = [Storage(StoragePathMacros.CACHE_FILE)])
class KotlinGradleFUSSettings : PersistentStateComponent<Element> {

    var gradleUserDirs: Set<String> = emptySet()

    override fun getState(): Element {
        val element = Element("KotlinGradleFUSSettings")
        gradleUserDirs.forEach {
            element.addOptionTag(KotlinGradleFUSSettings::gradleUserDirs.name, it)
        }
        return element
    }

    override fun loadState(state: Element) {
        val dirs = state.getChildren(Constants.OPTION)
            .filter { it.getAttributeValue(Constants.NAME) == KotlinGradleFUSSettings::gradleUserDirs.name }
            .mapNotNull { it.getAttributeValue(Constants.VALUE) }
            .toSet()
        gradleUserDirs = dirs
    }

    companion object {
        fun getInstance(project: Project): KotlinGradleFUSSettings = project.service()
    }
}

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