// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests

import com.intellij.openapi.externalSystem.importing.ImportSpec
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.kotlin.gradle.newTests.testFeatures.*
import org.jetbrains.kotlin.gradle.newTests.testServices.*
import org.jetbrains.kotlin.idea.base.test.AndroidStudioTestUtils
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradleImportingTestCase
import org.jetbrains.kotlin.idea.codeInsight.gradle.PluginTargetVersionsRule
import org.jetbrains.kotlin.idea.codeMetaInfo.clearTextFromDiagnosticMarkup
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.settings.GradleSystemSettings
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.File
import java.io.PrintStream

@RunWith(KotlinMppTestsJUnit4Runner::class)
@TestDataPath("\$PROJECT_ROOT/community/plugins/kotlin/idea/tests/testData/gradle")
abstract class AbstractKotlinMppGradleImportingTest :
    GradleImportingTestCase(), WorkspaceFilteringDsl, GradleProjectsPublishingDsl, GradleProjectsLinkingDsl, HighlightingCheckDsl,
    TestWithKotlinPluginAndGradleVersions, DevModeTweaksDsl {
    val kotlinTestPropertiesService: KotlinTestPropertiesService = KotlinTestPropertiesService.constructFromEnvironment()

    final override val gradleVersion: String
        // equal to this.gradleVersion, going through the Service for the sake of consistency
        get() = kotlinTestPropertiesService.gradleVersion.version

    final override val kotlinPluginVersion: KotlinToolingVersion
        get() = kotlinTestPropertiesService.kotlinGradlePluginVersion

    val importedProject: Project
        get() = myProject
    val importedProjectRoot: VirtualFile
        get() = myProjectRoot

    private val gradleProjectsPublishingService = GradleProjectsPublishingService
    private val gradleProjectLinkingService = GradleProjectLinkingService
    private val highlightingCheckService = HighlightingCheckService

    open fun TestConfigurationDslScope.defaultTestConfiguration() {}

    @get:Rule
    val workspaceModelTestingService = WorkspaceModelTestingService()

    @get:Rule
    val noErrorEventsDuringImportService = NoErrorEventsDuringImportService()

    @get:Rule
    val testDataDirectoryService = TestDataDirectoryService()

    @get:Rule
    val gradleDaemonWatchdogService = GradleDaemonWatchdogService

    @get:Rule
    val pluginTargetVersionRule = PluginTargetVersionsRule()

    protected fun doTest(configuration: TestConfigurationDslScope.() -> Unit = { }) {
        val defaultConfig = TestConfiguration().apply { defaultTestConfiguration() }
        val testConfig = defaultConfig.copy().apply { configuration() }
        doTest(testConfig)
    }

    private fun doTest(configuration: TestConfiguration) {
        createProjectSubFile(
            "local.properties",
            """
                |sdk.dir=${KotlinTestUtils.getAndroidSdkSystemIndependentPath()}
                |org.gradle.java.home=${findJdkPath()}
            """.trimMargin()
        )

        configureByFiles(testDataDirectoryService.testDataDirectory(), configuration)

        configuration.getConfiguration(LinkedProjectPathsTestsFeature).linkedProjectPaths.forEach {
            gradleProjectLinkingService.linkGradleProject(it, importedProjectRoot.toNioPath(), importedProject)
        }

        configuration.getConfiguration(GradleProjectsPublishingTestsFeature).publishedSubprojectNames.forEach {
            gradleProjectsPublishingService.publishSubproject(it, importedProjectRoot.toNioPath(), importedProject)
        }

        importProject()

        noErrorEventsDuringImportService.checkImportErrors(testDataDirectoryService)
        workspaceModelTestingService.checkWorkspaceModel(configuration, this)
        highlightingCheckService.runHighlightingCheckOnAllModules(configuration, this)
    }

    final override fun findJdkPath(): String {
        return System.getenv("JDK_11") ?: System.getenv("JAVA11_HOME") ?: run {
            val message = "Missing JDK_11 or JAVA11_HOME environment variable"
            if (IS_UNDER_TEAMCITY) LOG.error(message) else LOG.warn(message)
            super.findJdkPath()
        }
    }

    final override fun setUp() {
        // see KT-55554
        assumeTrue("Test is ignored because it requires Mac-host", HostManager.hostIsMac)
        // Hack: usually this is set-up by JUnit's Parametrized magic, but
        // our tests source versions from `kotlintestPropertiesService`, not from
        // @Parametrized
        this.gradleVersion = kotlinTestPropertiesService.gradleVersion.version
        super.setUp()

        // Otherwise Gradle Daemon fails with Metaspace exhausted periodically
        GradleSystemSettings.getInstance().gradleVmOptions =
            "-XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${System.getProperty("user.dir")}"
    }

    private fun configureByFiles(rootDir: File, testConfiguration: TestConfiguration): List<VirtualFile> {
        assert(rootDir.exists()) { "Directory ${rootDir.path} doesn't exist" }
        val devModeConfig = testConfiguration.getConfiguration(DevModeTestFeature)
        val writeTestProjectTo = devModeConfig.writeTestProjectTo
        val rootForProjectCopy = computeRootForProjectCopy(writeTestProjectTo, devModeConfig)
        rootForProjectCopy?.mkdirs()

        return rootDir.walk().mapNotNull {
            when {
                it.isDirectory -> null

                !it.name.endsWith(KotlinGradleImportingTestCase.AFTER_SUFFIX) -> {
                    val text = kotlinTestPropertiesService.substituteKotlinTestPropertiesInText(
                        clearTextFromDiagnosticMarkup(FileUtil.loadFile(it, /* convertLineSeparators = */ true)),
                        it
                    )
                    val relativeToRoot = it.path.substringAfter(rootDir.path + File.separator)
                    val virtualFile = createProjectSubFile(relativeToRoot, text)
                    if (rootForProjectCopy != null) {
                        val output = File(rootForProjectCopy, relativeToRoot)
                        output.parentFile.mkdirs()
                        output.createNewFile()
                        output.writeText(text)
                    }

                    // Real file with expected testdata allows to throw nicer exceptions in
                    // case of mismatch, as well as open interactive diff window in IDEA
                    virtualFile.putUserData(VfsTestUtil.TEST_DATA_FILE_PATH, it.absolutePath)

                    virtualFile
                }

                else -> null
            }
        }.toList()
    }

    private fun computeRootForProjectCopy(
        writeTestProjectTo: File?,
        devModeConfig: DevModeTweaksImpl
    ): File? {
        if (writeTestProjectTo == null) return null

        val rootForProjectCopy = File(writeTestProjectTo, testDirectoryName)

        when {
            !writeTestProjectTo.isDirectory ->
                error("Trying to write test project to ${writeTestProjectTo.canonicalPath}, but it's not a directory")

            rootForProjectCopy.exists() && devModeConfig.overwriteExistingProjectCopy ->
                rootForProjectCopy.deleteRecursively()

            rootForProjectCopy.exists() && rootForProjectCopy.listFiles().isNotEmpty() && !devModeConfig.overwriteExistingProjectCopy ->
                error("Asked to write test project to ${rootForProjectCopy.canonicalPath}, but it's not empty and 'overwriteExisting = true' isn't specified")
        }

       return rootForProjectCopy
    }

    final override fun importProject(skipIndexing: Boolean?) {
        AndroidStudioTestUtils.specifyAndroidSdk(File(projectPath))
        super.importProject(skipIndexing)
    }

    // To make source root checks more convenient: otherwise, each test will have to create some folders
    // in order respective content roots to be imported (and Git can't add empty folder, so one will have
    // to fill those content roots with some files even)
    final override fun createImportSpec(): ImportSpec {
        return ImportSpecBuilder(super.createImportSpec())
            .createDirectoriesForEmptyContentRoots()
            .build()
    }

    // super does plain `print` instead of `println`, so we need to
    // override it to preserve line breaks in output of Gradle-process
    final override fun printOutput(stream: PrintStream, text: String) {
        stream.println(text)
    }

    companion object {
        // TODO: enable on TC when monitoring comes
        var healthchecksEnabled: Boolean = false
    }
}
