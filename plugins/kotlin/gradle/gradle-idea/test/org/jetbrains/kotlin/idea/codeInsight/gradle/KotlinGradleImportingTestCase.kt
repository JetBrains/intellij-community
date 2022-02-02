// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.idea.test.GradleProcessOutputInterceptor
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.test.AndroidStudioTestUtils
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.utils.addToStdlib.filterIsInstanceWithChecker
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.service.project.open.createLinkSettings
import org.jetbrains.plugins.gradle.settings.GradleSystemSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Assume
import org.junit.runners.Parameterized
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

abstract class KotlinGradleImportingTestCase : GradleImportingTestCase() {
    public override fun getModule(name: String?): Module = super.getModule(name)

    protected open fun testDataDirName(): String = ""

    protected open fun clearTextFromMarkup(text: String): String = text

    protected open fun testDataDirectory(): File {
        val baseDir = IDEA_TEST_DATA_DIR.resolve("gradle/${testDataDirName()}")
        return File(baseDir, getTestName(true).substringBefore("_").substringBefore(" "))
    }

    protected val importStatusCollector = ImportStatusCollector()

    override fun setUp() {
        Assume.assumeFalse(AndroidStudioTestUtils.skipIncompatibleTestAgainstAndroidStudio())
        super.setUp()
        GradleSystemSettings.getInstance().gradleVmOptions =
            "-XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${System.getProperty("user.dir")}"
        GradleProcessOutputInterceptor.install(testRootDisposable)

        setUpImportStatusCollector()
    }

    override fun tearDown() {
        tearDownImportStatusCollector()
        super.tearDown()
    }

    protected open fun setUpImportStatusCollector() {
        ExternalSystemProgressNotificationManager
            .getInstance()
            .addNotificationListener(importStatusCollector)
    }

    protected open fun tearDownImportStatusCollector() {
        ExternalSystemProgressNotificationManager
            .getInstance()
            .removeNotificationListener(importStatusCollector)
    }

    protected fun configureKotlinVersionAndProperties(text: String, properties: Map<String, String>? = null): String {
        var result = text
        (properties ?: defaultProperties).forEach { (key, value) ->
            result = result.replace(Regex("""\{\s*\{\s*${key}\s*}\s*}"""), value)
        }
        return result
    }

    protected open val defaultProperties: Map<String, String> = mapOf("kotlin_plugin_version" to LATEST_STABLE_GRADLE_PLUGIN_VERSION)

    protected open fun configureByFiles(properties: Map<String, String>? = null): List<VirtualFile> {
        val rootDir = testDataDirectory()
        assert(rootDir.exists()) { "Directory ${rootDir.path} doesn't exist" }

        return rootDir.walk().mapNotNull {
            when {
                it.isDirectory -> null

                !it.name.endsWith(AFTER_SUFFIX) -> {
                    val text = configureKotlinVersionAndProperties(
                        clearTextFromMarkup(FileUtil.loadFile(it, /* convertLineSeparators = */ true)),
                        properties
                    )
                    val virtualFile = createProjectSubFile(it.path.substringAfter(rootDir.path + File.separator), text)

                    // Real file with expected testdata allows to throw nicer exceptions in
                    // case of mismatch, as well as open interactive diff window in IDEA
                    virtualFile.putUserData(VfsTestUtil.TEST_DATA_FILE_PATH, it.absolutePath)

                    virtualFile
                }

                else -> null
            }
        }.toList()
    }

    protected fun createLocalPropertiesSubFileForAndroid() {
        createProjectSubFile(
            "local.properties",
            "sdk.dir=/${KotlinTestUtils.getAndroidSdkSystemIndependentPath()}"
        )
    }

    protected fun checkFiles(files: List<VirtualFile>) {
        FileDocumentManager.getInstance().saveAllDocuments()

        files.filter {
            it.name == GradleConstants.DEFAULT_SCRIPT_NAME
                    || it.name == GradleConstants.KOTLIN_DSL_SCRIPT_NAME
                    || it.name == GradleConstants.SETTINGS_FILE_NAME
        }
            .forEach {
                if (it.name == GradleConstants.SETTINGS_FILE_NAME && !File(testDataDirectory(), it.name + AFTER_SUFFIX).exists()) return@forEach
                val actualText = configureKotlinVersionAndProperties(LoadTextUtil.loadText(it).toString())
                val expectedFileName = if (File(testDataDirectory(), it.name + ".$gradleVersion" + AFTER_SUFFIX).exists()) {
                    it.name + ".$gradleVersion" + AFTER_SUFFIX
                } else {
                    it.name + AFTER_SUFFIX
                }
                val expectedFile = File(testDataDirectory(), expectedFileName)
                KotlinTestUtils.assertEqualsToFile(expectedFile, actualText) { s -> configureKotlinVersionAndProperties(s) }
            }
    }

    override fun importProject() {
        AndroidStudioTestUtils.specifyAndroidSdk(File(projectPath))
        super.importProject()
    }

    protected fun importProjectFromTestData(): List<VirtualFile> {
        val files = configureByFiles()
        importProject()
        return files
    }

    protected fun getSourceRootInfos(moduleName: String): List<Pair<String, JpsModuleSourceRootType<*>>> {
        return ModuleRootManager.getInstance(getModule(moduleName)).contentEntries.flatMap { contentEntry ->
            contentEntry.sourceFolders.map { sourceFolder ->
                sourceFolder.url.replace(projectPath, "") to sourceFolder.rootType
            }
        }
    }

    override fun handleImportFailure(errorMessage: String, errorDetails: String?) {
        val gradleOutput = GradleProcessOutputInterceptor.getInstance()?.getOutput().orEmpty()

        // Typically Gradle error message consists of a line with the description of the error followed by
        // a multi-line stacktrace. The idea is to cut off the stacktrace if it is already contained in
        // the intercepted Gradle process output to avoid unnecessary verbosity.
        val compactErrorMessage = when (val indexOfNewLine = errorMessage.indexOf('\n')) {
            -1 -> errorMessage
            else -> {
                val compactErrorMessage = errorMessage.substring(0, indexOfNewLine)
                val theRest = errorMessage.substring(indexOfNewLine + 1)
                if (theRest in gradleOutput) compactErrorMessage else errorMessage
            }
        }

        val failureMessage = buildString {
            append("Gradle import failed: ").append(compactErrorMessage).append('\n')
            if (!errorDetails.isNullOrBlank()) append("Error details: ").append(errorDetails).append('\n')
            append("Gradle process output (BEGIN):\n")
            append(gradleOutput)
            if (!gradleOutput.endsWith('\n')) append('\n')
            append("Gradle process output (END)")
        }
        fail(failureMessage)
    }

    protected open fun assertNoBuildErrorEventsReported() {
        assertEmpty("No error events was expected to be reported", importStatusCollector.buildErrors)
    }

    protected open fun assertNoModuleDepForModule(moduleName: String, depName: String) {
        assertEmpty("No dependency '$depName' was expected", collectModuleDeps<ModuleOrderEntry>(moduleName, depName))
    }

    protected open fun assertNoLibraryDepForModule(moduleName: String, depName: String) {
        assertEmpty("No dependency '$depName' was expected", collectModuleDeps<LibraryOrderEntry>(moduleName, depName))
    }

    private inline fun <reified T : OrderEntry> collectModuleDeps(moduleName: String, depName: String): List<T> {
        return getRootManager(moduleName).orderEntries.asList().filterIsInstanceWithChecker { it.presentableName == depName }
    }

    protected fun linkProject(projectFilePath: String) {
        val localFileSystem = LocalFileSystem.getInstance()
        val projectFile = localFileSystem.refreshAndFindFileByPath(projectFilePath)
        ExternalSystemUtil.linkExternalProject(
            GradleConstants.SYSTEM_ID,
            createLinkSettings(projectFile!!.toNioPath(), myProject),
            myProject,
            null,
            false,
            ProgressExecutionMode.MODAL_SYNC
        )
    }

    protected fun runTaskAndGetErrorOutput(projectPath: String, taskName: String, scriptParameters: String = ""): String {
        val taskErrOutput = StringBuilder()
        val stdErrListener = object : ExternalSystemTaskNotificationListenerAdapter() {
            override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
                if (!stdOut) {
                    taskErrOutput.append(text)
                }
            }
        }
        val notificationManager = ExternalSystemProgressNotificationManager.getInstance()
        notificationManager.addNotificationListener(stdErrListener)
        try {
            val settings = ExternalSystemTaskExecutionSettings()
            settings.externalProjectPath = projectPath
            settings.taskNames = listOf(taskName)
            settings.scriptParameters = scriptParameters
            settings.externalSystemIdString = GradleConstants.SYSTEM_ID.id

            val future = CompletableFuture<String>()
            ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, myProject, GradleConstants.SYSTEM_ID,
                                       object : TaskCallback {
                                           override fun onSuccess() {
                                               future.complete(taskErrOutput.toString())
                                           }

                                           override fun onFailure() {
                                               future.complete(taskErrOutput.toString())
                                           }
                                       }, ProgressExecutionMode.IN_BACKGROUND_ASYNC)
            return future.get(10, TimeUnit.SECONDS)
        }
        finally {
            notificationManager.removeNotificationListener(stdErrListener)
        }
    }

    companion object {
        const val AFTER_SUFFIX = ".after"

        const val MINIMAL_SUPPORTED_GRADLE_PLUGIN_VERSION = "1.3.0"
        const val LATEST_STABLE_GRADLE_PLUGIN_VERSION = "1.3.70"

        val SUPPORTED_GRADLE_VERSIONS: List<Array<Any>> = listOf(arrayOf("4.9"), arrayOf("5.6.4"), arrayOf("6.0.1"))

        @JvmStatic
        @Suppress("ACCIDENTAL_OVERRIDE")
        @Parameterized.Parameters(name = "{index}: with Gradle-{0}")
        fun data(): Collection<Array<Any>> = SUPPORTED_GRADLE_VERSIONS
    }
}
