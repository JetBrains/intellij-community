// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.VfsTestUtil
import org.gradle.util.GradleVersion
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.AndroidStudioTestUtils
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModelBinary
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.getTestDataFileName
import org.jetbrains.kotlin.idea.test.TestMetadataUtil.getTestData
import org.jetbrains.kotlin.utils.addToStdlib.filterIsInstanceWithChecker
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.service.project.open.createLinkSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Assume
import org.junit.runners.Parameterized
import java.io.ByteArrayInputStream
import java.io.File
import java.io.ObjectInputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

@Suppress("ACCIDENTAL_OVERRIDE")
abstract class KotlinGradleImportingTestCase : GradleImportingTestCase(),
                                               ExpectedPluginModeProvider {

    public override fun getModule(name: String): Module = super.getModule(name)

    protected open fun testDataDirName(): String = ""

    protected open fun clearTextFromMarkup(text: String): String = text

    protected open fun testDataDirectory(): File {
        val clazz = this::class.java
        return getTestData(clazz)?.toString()?.let {
            val test = getTestDataFileName(clazz, getName()) ?: error("No @TestMetadata for ${clazz.name}")
            File(it, test)
        } ?: run {
            val baseDir = IDEA_TEST_DATA_DIR.resolve("gradle/${testDataDirName()}")
            File(baseDir, getTestName(true).substringBefore("_").substringBefore(" "))
        }
    }

    protected val importStatusCollector = ImportStatusCollector()

    override fun requireJdkHome(): String {
        /*
        https://docs.gradle.org/current/userguide/compatibility.html
         */
        return if (currentGradleVersion >= GradleVersion.version("7.3")) {
            /* Version 7.3 or higher supports JDK_17 */
            System.getenv("JDK_17_0") ?: System.getenv("JDK_17") ?: System.getenv("JAVA17_HOME") ?: run {
                val message = "Missing JDK_17_0 or JAVA17_HOME environment variable"
                if (IS_UNDER_TEAMCITY) LOG.error(message) else LOG.warn(message)
                super.requireJdkHome()
            }
        } else {
            /* Versions below 7.3 shall run with JDK 11 (supported since Gradle 5) */
            System.getenv("JDK_11") ?: System.getenv("JAVA11_HOME") ?: run {
                val message = "Missing JDK_11 or JAVA11_HOME environment variable"
                if (IS_UNDER_TEAMCITY) LOG.error(message) else LOG.warn(message)
                super.requireJdkHome()
            }
        }
    }

    override fun setUp() {
        Assume.assumeFalse(AndroidStudioTestUtils.skipIncompatibleTestAgainstAndroidStudio())
        setUpWithKotlinPlugin { super.setUp() }
        GradleProcessOutputInterceptor.install(testRootDisposable)

        setUpImportStatusCollector()
    }

    override fun configureGradleVmOptions(options: MutableSet<String>) {
        super.configureGradleVmOptions(options)
        options.add("-XX:MaxMetaspaceSize=512m")
        options.add("-XX:+HeapDumpOnOutOfMemoryError")
        options.add("-XX:HeapDumpPath=${System.getProperty("user.dir")}")
    }

    override fun tearDown() {
        try {
            tearDownImportStatusCollector()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K1

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

    protected open fun configureKotlinVersionAndProperties(text: String, properties: Map<String, String>? = null): String {
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

    @Deprecated("Use .setupAndroid() instead", level = DeprecationLevel.ERROR)
    protected fun createLocalPropertiesSubFileForAndroid() {
        createProjectSubFile(
            "local.properties",
            "sdk.dir=/${KotlinTestUtils.getAndroidSdkSystemIndependentPath()}"
        )
    }

    protected fun checkFiles(files: List<VirtualFile>, properties: Map<String, String>? = null) {
        FileDocumentManager.getInstance().saveAllDocuments()

        files.filter {
            it.name == GradleConstants.DEFAULT_SCRIPT_NAME
                    || it.name == GradleConstants.KOTLIN_DSL_SCRIPT_NAME
                    || it.name == GradleConstants.SETTINGS_FILE_NAME
                    || it.name == GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME
        }
            .forEach {
                if (it.name == GradleConstants.SETTINGS_FILE_NAME &&
                    !File(testDataDirectory(), GradleConstants.SETTINGS_FILE_NAME + AFTER_SUFFIX).exists()
                ) return@forEach
                if (it.name == GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME &&
                    !File(testDataDirectory(), GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME + AFTER_SUFFIX).exists()
                ) return@forEach

                val actualText = configureKotlinVersionAndProperties(LoadTextUtil.loadText(it).toString(), properties)
                val expectedFileName = if (File(testDataDirectory(), it.name + ".$gradleVersion" + AFTER_SUFFIX).exists()) {
                    it.name + ".$gradleVersion" + AFTER_SUFFIX
                } else {
                    it.name + AFTER_SUFFIX
                }
                val expectedFile = File(testDataDirectory(), expectedFileName)
                KotlinTestUtils.assertEqualsToFile(expectedFile, actualText) { s -> configureKotlinVersionAndProperties(s, properties) }
            }
    }

    /**
     * Compares expected (with ".after" postfix) and actual files with directory traversal.
     */
    protected fun checkFilesInMultimoduleProject(
        files: List<VirtualFile>,
        subModules: List<String>,
        properties: Map<String, String>? = null
    ) {
        FileDocumentManager.getInstance().saveAllDocuments()

        files.filter {
            it.name == GradleConstants.DEFAULT_SCRIPT_NAME
                    || it.name == GradleConstants.KOTLIN_DSL_SCRIPT_NAME
                    || it.name == GradleConstants.SETTINGS_FILE_NAME
                    || it.name == GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME
        }
            .forEach {
                if (it.name == GradleConstants.SETTINGS_FILE_NAME &&
                    !File(testDataDirectory(), GradleConstants.SETTINGS_FILE_NAME + AFTER_SUFFIX).exists()
                ) return@forEach
                if (it.name == GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME &&
                    !File(testDataDirectory(), GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME + AFTER_SUFFIX).exists()
                ) return@forEach

                val actualText = configureKotlinVersionAndProperties(LoadTextUtil.loadText(it).toString(), properties)
                var moduleForBuildScript = ""
                for (module in subModules) {
                    if (it.path.substringBefore("/" + it.name).endsWith(module)) {
                        moduleForBuildScript = module
                        break
                    }
                }
                val expectedFileName =
                    if (File(File(testDataDirectory(), moduleForBuildScript), it.name + ".$gradleVersion" + AFTER_SUFFIX).exists()) {
                        it.name + ".$gradleVersion" + AFTER_SUFFIX
                    } else {
                        it.name + AFTER_SUFFIX
                    }
                val expectedFile = File(
                    testDataDirectory(), if (moduleForBuildScript.isNotEmpty()) {
                        "$moduleForBuildScript/$expectedFileName"
                    } else expectedFileName
                )
                KotlinTestUtils.assertEqualsToFile(expectedFile, actualText) { s -> configureKotlinVersionAndProperties(s, properties) }
            }
    }

    override fun importProject(skipIndexing: Boolean?) {
        AndroidStudioTestUtils.specifyAndroidSdk(File(projectPath))
        super.importProject(skipIndexing)
    }

    protected fun importProjectFromTestData(): List<VirtualFile> {
        val files = configureByFiles()
        importProject()
        return files
    }

    protected inline fun <reified T : Any> buildGradleModel(debuggerOptions: BuildGradleModelDebuggerOptions? = null): BuiltGradleModel<T> =
        buildGradleModel(T::class, debuggerOptions)

    protected fun <T : Any> buildGradleModel(
        clazz: KClass<T>,
        debuggerOptions: BuildGradleModelDebuggerOptions? = null
    ): BuiltGradleModel<T> =
        buildGradleModel(
            myProjectRoot.toNioPath().toFile(),
            GradleVersion.version(gradleVersion),
            requireJdkHome(),
            clazz,
            debuggerOptions
        )

    protected fun buildKotlinMPPGradleModel(
        debuggerOptions: BuildGradleModelDebuggerOptions? = null
    ): BuiltGradleModel<KotlinMPPGradleModel> = buildGradleModel<KotlinMPPGradleModelBinary>(debuggerOptions)
        .map { model -> ObjectInputStream(ByteArrayInputStream(model.data)).readObject() as KotlinMPPGradleModel }

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

    protected fun linkProject(projectFilePath: String = projectPath) {
        val localFileSystem = LocalFileSystem.getInstance()
        val projectFile = localFileSystem.refreshAndFindFileByPath(projectFilePath)
            ?: error("Failed to find projectFile: $projectFilePath")

        val settings = createLinkSettings(projectFile.toNioPath(), myProject)

        ExternalSystemUtil.linkExternalProject(
            /* externalSystemId = */ GradleConstants.SYSTEM_ID,
            /* projectSettings = */ settings,
            /* project = */ myProject,
            /* importResultCallback = */ null,
            /* isPreviewMode = */ false,
            /* progressExecutionMode = */ ProgressExecutionMode.MODAL_SYNC
        )
    }

    protected fun runTaskAndGetErrorOutput(projectPath: String, taskName: String, scriptParameters: String = ""): String {
        val taskErrOutput = StringBuilder()
        val stdErrListener = object : ExternalSystemTaskNotificationListener {
            override fun onTaskOutput(id: ExternalSystemTaskId, text: String, processOutputType: ProcessOutputType) {
                if (processOutputType.isStderr) {
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
            ExternalSystemUtil.runTask(
                settings, DefaultRunExecutor.EXECUTOR_ID, myProject, GradleConstants.SYSTEM_ID,
                object : TaskCallback {
                    override fun onSuccess() {
                        future.complete(taskErrOutput.toString())
                    }

                    override fun onFailure() {
                        future.complete(taskErrOutput.toString())
                    }
                }, ProgressExecutionMode.IN_BACKGROUND_ASYNC
            )
            return future.get(10, TimeUnit.SECONDS)
        } finally {
            notificationManager.removeNotificationListener(stdErrListener)
        }
    }

    companion object {
        const val AFTER_SUFFIX = ".after"

        const val LATEST_STABLE_GRADLE_PLUGIN_VERSION = "2.0.0"

        val SUPPORTED_GRADLE_VERSIONS = arrayOf("6.8.3", "7.6")

        // https://kotlinlang.org/docs/gradle-configure-project.html#targeting-the-jvm
        val GRADLE_TO_KGP_VERSION = mapOf(
            "7.6.4" to "1.9.10",
            "8.6" to "1.9.20"
        )

        @JvmStatic
        @Suppress("ACCIDENTAL_OVERRIDE")
        @Parameterized.Parameters(name = "{index}: with Gradle-{0}")
        fun data(): Collection<Array<Any>> = SUPPORTED_GRADLE_VERSIONS.map { arrayOf(it) }
    }
}

fun GradleImportingTestCase.enableExperimentalMPP(enable: Boolean) {
    //enable experimental MPP features e.g. an import K/JS run tasks
    (AdvancedSettings.getInstance() as AdvancedSettingsImpl).setSetting("kotlin.mpp.experimental", enable, testRootDisposable)
}
