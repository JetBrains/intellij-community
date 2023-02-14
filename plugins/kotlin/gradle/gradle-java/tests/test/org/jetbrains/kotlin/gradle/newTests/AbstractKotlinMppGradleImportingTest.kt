// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.newTests

import com.intellij.openapi.externalSystem.importing.ImportSpec
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.kotlin.gradle.newTests.testFeatures.*
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.*
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.contentRoots.ContentRootsChecker
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.facets.KotlinFacetSettingsChecker
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.highlighting.HighlightingCheckDsl
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.highlighting.HighlightingChecker
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.orderEntries.OrderEntriesChecker
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.workspace.GeneralWorkspaceChecks
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.workspace.WorkspaceChecksDsl
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
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.io.File
import java.io.PrintStream

@RunWith(KotlinMppTestsJUnit4Runner::class)
@TestDataPath("\$PROJECT_ROOT/community/plugins/kotlin/idea/tests/testData/gradle")
abstract class AbstractKotlinMppGradleImportingTest :
    GradleImportingTestCase(), WorkspaceChecksDsl, GradleProjectsPublishingDsl, GradleProjectsLinkingDsl, HighlightingCheckDsl,
    TestWithKotlinPluginAndGradleVersions, DevModeTweaksDsl {

    internal val installedFeatures = listOf<TestFeature<*>>(
        GradleProjectsPublishingTestsFeature,
        LinkedProjectPathsTestsFeature,
        NoErrorEventsDuringImportFeature,

        HighlightingChecker,
        ContentRootsChecker,
        KotlinFacetSettingsChecker,
        OrderEntriesChecker,
        TestTasksChecker,
    )

    private val context: KotlinMppTestsContextImpl = KotlinMppTestsContextImpl()

    @get:Rule
    val testDescriptionProviderJUnitRule = TestDescriptionProviderJUnitRule(context)
    @get:Rule
    val testFeaturesBeforeAfterJUnit4Adapter = TestFeaturesBeforeAfterJUnit4Adapter()

    @get:Rule
    val pluginTargetVersionRule = PluginTargetVersionsRule()

    // Two properties below are needed solely for compatibility with PluginTargetVersionsRule;
    // please, use context.testPropertiesService if you need those versions in your code
    final override val gradleVersion: String
        get() = context.gradleVersion.version

    final override val kotlinPluginVersion: KotlinToolingVersion
        get() = context.kgpVersion


    open fun TestConfigurationDslScope.defaultTestConfiguration() {}

    protected fun doTest(configuration: TestConfigurationDslScope.() -> Unit = { }) {
        val defaultConfig = TestConfiguration().apply { defaultTestConfiguration() }
        val testConfig = defaultConfig.copy().apply { configuration() }
        context.testConfiguration = testConfig
        context.doTest()
    }

    private fun KotlinMppTestsContextImpl.doTest() {
        installedFeatures.forEach { feature -> with(feature) { context.beforeTestExecution() } }
        createProjectSubFile(
            "local.properties",
            """
                |sdk.dir=${KotlinTestUtils.getAndroidSdkSystemIndependentPath()}
                |org.gradle.java.home=${findJdkPath()}
            """.trimMargin()
        )

        configureByFiles()

        installedFeatures.forEach { feature -> with(feature) { context.beforeImport() } }

        importProject()

        installedFeatures.forEach { feature ->
            with(feature) {
                if (feature !is AbstractTestChecker<*> || isCheckerEnabled(feature)) context.afterImport()
            }
        }
    }

    private fun KotlinMppTestsContextImpl.isCheckerEnabled(checker: AbstractTestChecker<*>): Boolean {
        // Temporary mute TEST_TASKS checks due to issues with hosts on CI. See KT-56332
        if (checker is TestTasksChecker) return false

        val config = testConfiguration.getConfiguration(GeneralWorkspaceChecks)
        return when {
            config.disableCheckers != null -> checker !in config.disableCheckers!!

            config.onlyCheckers != null -> checker in config.onlyCheckers!!
                    // Highlighting checker should be disabled explicitly, because it's rarely the intention to not run
                    // highlighting when you have sources and say 'onlyCheckers(OrderEntriesCheckers)'
                    || checker is HighlightingChecker
            else -> true
        }
    }

    final override fun findJdkPath(): String {
        return System.getenv("JDK_17") ?: System.getenv("JDK_17_0") ?: System.getenv("JAVA17_HOME") ?: run {
            val message = "Missing JDK_17 or JDK_17_0 or JAVA17_HOME  environment variable"
            if (IS_UNDER_TEAMCITY) LOG.error(message) else LOG.warn(message)
            super.findJdkPath()
        }
    }

    final override fun setUp() {
        // see KT-55554
        assumeTrue("Test is ignored because it requires Mac-host", HostManager.hostIsMac)
        // Hack: usually this is set-up by JUnit's Parametrized magic, but
        // our tests source versions from `kotlinTestPropertiesService`, not from
        // @Parametrized
        this.gradleVersion = context.gradleVersion.version
        super.setUp()

        context.testProject = myProject
        context.testProjectRoot = myProjectRoot.toNioPath().toFile()

        // Otherwise Gradle Daemon fails with Metaspace exhausted periodically
        GradleSystemSettings.getInstance().gradleVmOptions =
            "-XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${System.getProperty("user.dir")}"
    }

    private fun KotlinMppTestsContext.configureByFiles(): List<VirtualFile> {
        val rootDir = context.testDataDirectory
        assert(rootDir.exists()) { "Directory ${rootDir.path} doesn't exist" }
        val devModeConfig = testConfiguration.getConfiguration(DevModeTestFeature)
        val writeTestProjectTo = devModeConfig.writeTestProjectTo
        val rootForProjectCopy = computeRootForProjectCopy(writeTestProjectTo, devModeConfig)
        rootForProjectCopy?.mkdirs()

        return rootDir.walk().mapNotNull {
            when {
                it.isDirectory -> null

                !it.name.endsWith(KotlinGradleImportingTestCase.AFTER_SUFFIX) -> {
                    val text = context.testProperties.substituteKotlinTestPropertiesInText(
                        clearTextFromDiagnosticMarkup(FileUtil.loadFile(it, /* convertLineSeparators = */ true)),
                        it
                    )
                    val preprocessedText = installedFeatures.fold(text) { currentText, nextFeature ->
                        nextFeature.preprocessFile(it, currentText) ?: currentText
                    }
                    val relativeToRoot = it.path.substringAfter(rootDir.path + File.separator)
                    val virtualFile = createProjectSubFile(relativeToRoot, preprocessedText)
                    if (rootForProjectCopy != null) {
                        val output = File(rootForProjectCopy, relativeToRoot)
                        output.parentFile.mkdirs()
                        output.createNewFile()
                        output.writeText(preprocessedText)
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

    class TestDescriptionProviderJUnitRule(private val testContext: KotlinMppTestsContextImpl) : KotlinBeforeAfterTestRuleWithDescription {
        override fun before(description: Description) {
            testContext.description = description
        }
    }

    class TestFeaturesBeforeAfterJUnit4Adapter : KotlinBeforeAfterTestRuleWithTarget {
        private val testFeaturesCompletedSetUp: MutableList<TestFeatureWithSetUpTearDown<*>> = mutableListOf()

        override fun before(target: Any) {
            require(target is AbstractKotlinMppGradleImportingTest) {
                "TeatFeaturesBeforeAfterJUnit4Adapter can only be used in inheritors of AbstractKotlinMppGradleImportingTest"
            }
            testFeaturesCompletedSetUp.clear()
            target.installedFeatures.filterIsInstance<TestFeatureWithSetUpTearDown<*>>().forEach {
                it.additionalSetUp()
                testFeaturesCompletedSetUp += it
            }
        }

        override fun after(target: Any) {
            // Make sure to call tearDown on those and only those features that executed setUp
            testFeaturesCompletedSetUp.forEach { it.additionalTearDown() }
        }
    }
}
