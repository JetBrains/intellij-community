// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests

import com.intellij.openapi.externalSystem.importing.ImportSpec
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.kotlin.gradle.newTests.testFeatures.GradleProjectsPublishingDsl
import org.jetbrains.kotlin.gradle.newTests.testFeatures.GradleProjectsPublishingTestsFeature
import org.jetbrains.kotlin.gradle.newTests.testFeatures.WorkspaceFilteringDsl
import org.jetbrains.kotlin.gradle.newTests.testServices.*
import org.jetbrains.kotlin.idea.base.test.AndroidStudioTestUtils
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradleImportingTestCase
import org.jetbrains.kotlin.idea.codeMetaInfo.clearTextFromDiagnosticMarkup
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner
import java.io.File

@RunWith(BlockJUnit4ClassRunner::class)
abstract class AbstractKotlinMppGradleImportingTest(
    testDataRoot: String
) : GradleImportingTestCase(), WorkspaceFilteringDsl, GradleProjectsPublishingDsl {
    val importedProject: Project
        get() = myProject
    val importedProjectRoot: VirtualFile
        get() = myProjectRoot

    val kotlinTestPropertiesService: KotlinTestPropertiesService = KotlinTestPropertiesServiceImpl()
    private val gradleProjectsPublishingService = GradleProjectsPublishingService

    open fun TestConfigurationDslScope.defaultTestConfiguration() { }

    @get:Rule
    val workspaceModelTestingService = WorkspaceModelTestingService()

    @get:Rule
    val noErrorEventsDuringImportService = NoErrorEventsDuringImportService()

    @get:Rule
    val testDataDirectoryService = TestDataDirectoryService(testDataRoot)

    @get:Rule
    val gradleDaemonWatchdogService = GradleDaemonWatchdogService

    protected fun doTest(configuration: TestConfigurationDslScope.() -> Unit = { }) {
        val defaultConfig = TestConfiguration().apply { defaultTestConfiguration() }
        val testConfig = defaultConfig.copy().apply { configuration() }
        doTest(testConfig)
    }

    private fun doTest(configuration: TestConfiguration) {
        configureByFiles(testDataDirectoryService.testDataDirectory())

        configuration.getConfiguration(GradleProjectsPublishingTestsFeature).publishedSubprojectNames.forEach {
            gradleProjectsPublishingService.publishSubproject(it, importedProjectRoot.toNioPath(), importedProject)
        }

        importProject()
        workspaceModelTestingService.checkWorkspaceModel(configuration, this)
    }

    final override fun findJdkPath(): String {
        return System.getenv("JDK_11") ?: System.getenv("JAVA11_HOME") ?: run {
            val message = "Missing JDK_11 or JAVA11_HOME environment variable"
            if (IS_UNDER_TEAMCITY) LOG.error(message) else LOG.warn(message)
            super.findJdkPath()
        }
    }

    override fun setUp() {
        // Hack: usually this is set-up by JUnit's Parametrized magic, but
        // our tests source versions from `kotlintestPropertiesService`, not from
        // @Parametrized
        this.gradleVersion = kotlinTestPropertiesService.gradleVersion
        super.setUp()
    }

    private fun configureByFiles(rootDir: File, properties: Map<String, String>? = null): List<VirtualFile> {
        assert(rootDir.exists()) { "Directory ${rootDir.path} doesn't exist" }

        return rootDir.walk().mapNotNull {
            when {
                it.isDirectory -> null

                !it.name.endsWith(KotlinGradleImportingTestCase.AFTER_SUFFIX) -> {
                    val text = kotlinTestPropertiesService.substituteKotlinTestPropertiesInText(
                        clearTextFromDiagnosticMarkup(FileUtil.loadFile(it, /* convertLineSeparators = */ true)),
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
}
