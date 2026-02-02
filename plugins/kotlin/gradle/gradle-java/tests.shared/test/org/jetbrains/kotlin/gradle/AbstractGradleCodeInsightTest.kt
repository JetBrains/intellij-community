// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.utils.vfs.getPsiFile
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.getMethodMetadata
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.getTestDataFileName
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.getTestsRoot
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.toSlashEndingDirPath
import org.jetbrains.kotlin.idea.test.TestFiles
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatKotlinIsSupported
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import java.io.File
import java.lang.reflect.Method

abstract class AbstractGradleCodeInsightTest : AbstractKotlinGradleCodeInsightBaseTest() {

    protected open val filesBasedTest: Boolean = true

    @TestDisposable
    protected lateinit var testRootDisposable: Disposable

    protected lateinit var testInfo: TestInfo

    @BeforeEach
    fun init(testInfo: TestInfo) {
        this.testInfo = testInfo
    }

    private var _testDataFiles: List<TestFile>? = null
    val testDataFiles: List<TestFile>
        get() = requireNotNull(_testDataFiles) {
            "testDataFiles have not been setup. Please use [AbstractGradleCodeInsightBaseTestCase.test] function inside your tests."
        }

    val mainTestDataFile: TestFile
        get() = requireNotNull(testDataFiles.firstOrNull()) {
            "expected at least one testDataFiles."
        }

    val mainTestDataPsiFile: PsiFile
        get() = runReadAction { getFile(mainTestDataFile.path).getPsiFile(project) }

    override fun setUp() {
        assumeThatKotlinIsSupported(gradleVersion)

        super.setUp()

        loadTestDataFiles()
        testDataFiles.forEach {
            writeTextAndCommit(it.path, it.content)
        }
    }

    override fun tearDown() {
        runAll(
            { _testDataFiles = null },
            { KotlinSdkType.removeKotlinSdkInTests() },
            { super.tearDown() }
        )
    }

    open fun getTestDataDirectory(testName: String): File {
        val clazz = this::class.java
        val root = getTestsRoot(clazz)

        if (filesBasedTest) {
            return File(root)
        }

        val test = getTestDataFileName(clazz, testName) ?: error("No @TestMetadata for ${clazz.name}")
        return File(root, test)
    }

    fun getTestDataPath(): String = toSlashEndingDirPath(getTestDataDirectory(retrieveTestMethod().name).path)

    private fun retrieveTestMethod(): Method = testInfo.testMethod.get()

    protected fun dataFile(fileName: String): File = File(getTestDataPath(), fileName)

    protected fun dataFile(): File? {
        val testMetadataFileName = fileName() ?: return null
        return dataFile(testMetadataFileName)
    }

    protected open fun fileName(): String? = getMethodMetadata(testInfo.testMethod.get())

    protected val document: Document
        get() {
            val fileEditorManager = FileEditorManager.getInstance(project) as FileEditorManagerEx
            return fileEditorManager.selectedTextEditor?.document ?: error("no document found")
        }

    protected open val testFileFactory = object : TestFiles.TestFileFactoryNoModules<TestFile>() {
        override fun create(fileName: String, text: String, directives: Directives): TestFile {
            val linesWithoutDirectives = text.lines().filter { !it.startsWith("// FILE") }
            return TestFile(fileName, linesWithoutDirectives.joinToString(separator = "\n"), directives)
        }
    }

    private fun loadTestDataFiles() {
        val mainFile = dataFile()
        if (mainFile == null) {
            // when a test method doesn't have @TestMetadata
            _testDataFiles = emptyList()
            return
        }
        val multiFileText = FileUtil.loadFile(mainFile, true)

        _testDataFiles = TestFiles.createTestFiles(
            mainFile.name,
            multiFileText,
            testFileFactory
        )
    }

    class TestFile internal constructor(val path: String, val content: String, val directives: Directives)

    companion object {
        @JvmStatic
        protected val GRADLE_VERSION_CATALOGS_FIXTURE = GradleTestFixtureBuilder.create("version-catalogs-kotlin-dsl") { gradleVersion ->
            withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                setProjectName("version-catalogs-kotlin-dsl")
                includeBuild("includedBuild1")
                includeBuild("includedBuildWithoutSettings")
                addCode(
                    $$"""
                    fun includeSubprojectsDynamically(path: String) {
                        val dirsWithBuildScripts = file(path).listFiles()
                            ?.filter { File(it, "build.gradle.kts").exists() }
                        dirsWithBuildScripts?.forEach { subproject ->
                            val relativePath = subproject.relativeTo(rootDir).path
                            include(":${relativePath.replace('/', ':')}")
                        }
                    }
                    includeSubprojectsDynamically("subprojectsDir")
                    """.trimIndent()
                )
            }
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                withKotlinDsl()
                withMavenCentral()
            }
            withFile("gradle/libs.versions.toml", "")
            // subprojects files
            withBuildFile(gradleVersion, "subprojectsDir/subproject1", gradleDsl = GradleDsl.KOTLIN) {
                withKotlinDsl()
                withMavenCentral()
            }
            // included build files
            withSettingsFile(gradleVersion, "includedBuild1", gradleDsl = GradleDsl.KOTLIN) { }
            withBuildFile(gradleVersion, "includedBuild1", gradleDsl = GradleDsl.KOTLIN) {
                withKotlinMultiplatformPlugin()
                withMavenCentral()
            }
            withFile("includedBuild1/gradle/libs.versions.toml", "")
            // included build without settings
            withBuildFile(gradleVersion, "includedBuildWithoutSettings", gradleDsl = GradleDsl.KOTLIN) {}
            withFile("includedBuildWithoutSettings/gradle/libs.versions.toml", "")
        }
    }
}