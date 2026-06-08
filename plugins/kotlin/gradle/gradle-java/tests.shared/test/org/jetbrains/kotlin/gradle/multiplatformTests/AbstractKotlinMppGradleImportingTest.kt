// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.externalSystem.importing.ImportSpec
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.utils.io.deleteRecursively
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.CustomGradlePropertiesDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.CustomGradlePropertiesTestFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.DevModeTestFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.DevModeTweaksDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.DevModeTweaksImpl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.GradleProjectsLinkingDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.GradleProjectsPublishingDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.GradleProjectsPublishingTestsFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.LibraryKindsChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.LinkedProjectPathsTestsFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.NoErrorEventsDuringImportFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.AllFilesAreUnderContentRootChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.AllFilesUnderContentRootConfigurationDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.DocumentationChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.DocumentationCheckerDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.ReferenceTargetChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.TestTasksChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.contentRoots.ContentRootsChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.facets.KotlinFacetSettingsChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting.HighlightingCheckDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting.HighlightingChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.hooks.KotlinMppTestHooks
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.hooks.KotlinMppTestHooksDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.orderEntries.OrderEntriesChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.runConfigurations.ExecuteRunConfigurationsChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.runConfigurations.RunConfigurationChecksDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.runConfigurations.RunConfigurationsChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.sources.LibrarySourcesCheckDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.sources.LibrarySourcesChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.workspace.WorkspaceChecksDsl
import org.jetbrains.kotlin.idea.base.test.AndroidStudioTestUtils
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradleImportingTestCase
import org.jetbrains.kotlin.idea.codeInsight.gradle.PluginTargetVersionsRule
import org.jetbrains.kotlin.idea.codeInsight.gradle.combineMultipleFailures
import org.jetbrains.kotlin.idea.codeMetaInfo.clearTextFromDiagnosticMarkup
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.junit.Assert
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Path
import java.util.TreeSet
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText

/**
 * The base class for Kotlin MPP Import-based tests.
 *
 * # Usage
 * 1. inherit it in your test-suite
 *
 * 2. Add `@TestMetadata` on the test suite to define a subpath relative to
 * `community/plugins/kotlin/idea/tests/testData/gradle`
 *
 * 3. Define tests inside as usual
 *
 * 4. In each test, call `doTest()` to run the test.
 * It will by default execute all [TestFeature]s and [AbstractTestChecker]s declared in [installedFeatures]
 *
 * 5. You can override [defaultTestConfiguration] to tweak the checks globally for all the tests in suite,
 * or `doTest { ... }`-block to tweak it locally for the specific test
 *
 * # Overrides, JUnit `@Rule`s, inheritance, extensibility
 *
 * This class **is explicitly designed to forbid extension by inheritance**. Most of overrides from [GradleImportingTestCase]
 * are intentionally `final`. Some methods and fields are not private due to JUnit restrictions, but it is heavily discouraged to
 * tweak them in inheritors.
 *
 * Avoid using [Rule] in inheritors to not complicate lifecycle any further, instead use [TestFeatureWithSetUpTearDown].
 * [KotlinSyncTestsContext.description] should cover common needs for [Rule]
 *
 * Sharing of the test suite capabilities should be done via standalone composable modularized [TestFeature]s
 *
 */
@RunWith(KotlinMppTestsJUnit4Runner::class)
@TestDataPath($$"$PROJECT_ROOT/community/plugins/kotlin/idea/tests/testData/gradle")
abstract class AbstractKotlinMppGradleImportingTest : GradleImportingTestCase(),
    WorkspaceChecksDsl, GradleProjectsPublishingDsl, GradleProjectsLinkingDsl,
    HighlightingCheckDsl,
    TestWithKotlinPluginAndGradleVersions, TestWithAndroidVersion, DevModeTweaksDsl,
    AllFilesUnderContentRootConfigurationDsl, RunConfigurationChecksDsl,
    CustomGradlePropertiesDsl, DocumentationCheckerDsl, KotlinMppTestHooksDsl,
    LibrarySourcesCheckDsl {

    internal val installedFeatures = listOf<TestFeature<*>>(
        GradleProjectsPublishingTestsFeature,
        LinkedProjectPathsTestsFeature,
        NoErrorEventsDuringImportFeature,
        CustomGradlePropertiesTestFeature,

        ContentRootsChecker,
        KotlinFacetSettingsChecker,
        OrderEntriesChecker,
        TestTasksChecker,
        HighlightingChecker,
        RunConfigurationsChecker,
        ExecuteRunConfigurationsChecker,
        AllFilesAreUnderContentRootChecker,
        DocumentationChecker,
        ReferenceTargetChecker,
        KotlinMppTestHooks,
        LibraryKindsChecker,
        LibrarySourcesChecker
    )

    private val context: KotlinMppTestsContext = KotlinMppTestsContext(installedFeatures)

    @get:Rule
    val testDescriptionProviderJUnitRule = TestDescriptionProviderJUnitRule(context)

    @get:Rule
    val testFeaturesBeforeAfterJUnit4Adapter = TestFeaturesBeforeAfterJUnit4Adapter()

    @get:Rule
    val pluginTargetVersionRule = PluginTargetVersionsRule()

    // Two properties below are needed solely for compatibility with PluginTargetVersionsRule;
    // please, use context.testPropertiesService if you need those versions in your code
    final override var testGradleVersion: TestVersion<GradleVersion>
        get() = context.testProperties.gradleVersion
        set(_) {}

    final override val kotlinPluginVersion: TestVersion<KotlinToolingVersion>
        get() = context.testProperties.kotlinVersion

    final override val agpVersion: TestVersion<String>?
        get() = context.testProperties.agpVersion

    // Temporary hack allowing to reuse new test runner in selected smoke tests for runs on linux-hosts
    open val allowOnNonMac: Boolean = false

    open fun TestConfigurationDslScope.defaultTestConfiguration() {}

    protected fun doTest(
        runImport: Boolean = true,
        afterImport: (KotlinMppTestsContext) -> Unit = { },
        testSpecificConfiguration: TestConfigurationDslScope.() -> Unit = { },
    ) {
        context.testConfiguration.defaultTestConfiguration()
        context.testConfiguration.testSpecificConfiguration()
        context.doTest(runImport, afterImport)
    }

    private fun KotlinMppTestsContext.doTest(runImport: Boolean, afterImport: (KotlinMppTestsContext) -> Unit = { }) {
        runAll(
            {
                runForEnabledFeatures { context.beforeTestExecution() }
                createLocalPropertiesFile()
                configureByFiles()
                runForEnabledFeatures { context.beforeImport() }
                if (runImport) {
                    importProject()
                }
                afterImport.invoke(context)

                runForEnabledFeatures { context.afterImport() }
            },
            {
                runForEnabledFeatures { context.afterTestExecution() }
            }
        )
    }

    private fun KotlinMppTestsContext.runForEnabledFeatures(action: TestFeature<*>.() -> Unit) {
        enabledFeatures.combineMultipleFailures { feature ->
            with(feature) { action() }
        }
    }

    private fun createLocalPropertiesFile() {
        createProjectSubFile(
            "local.properties",
            """
                |sdk.dir=${KotlinTestUtils.getAndroidSdkSystemIndependentPath()}
                |org.gradle.java.home=${requireJdkHome()}
            """.trimMargin()
        )
    }

    final override fun requireJdkHome(): String {
        return System.getenv("JDK_17") ?: System.getenv("JDK_17_0") ?: System.getenv("JAVA17_HOME") ?: run {
            val message = "Missing JDK_17 or JDK_17_0 or JAVA17_HOME  environment variable"
            if (IS_UNDER_TEAMCITY) LOG.error(message) else LOG.warn(message)
            super.requireJdkHome()
        }
    }

    final override fun setUp() {
        // see KT-55554
        if (!allowOnNonMac) {
            assumeTrue("Test is ignored because it requires Mac-host", HostManager.hostIsMac)
        }

        // Hack: usually this is set-up by JUnit's Parametrized magic, but
        // our tests source versions from `kotlinTestPropertiesService`, not from
        // @Parametrized
        (this as GradleImportingTestCase).gradleVersion = testGradleVersion.version.version
        super.setUp()

        context.testProject = myProject
        context.testProjectRoot = myProjectRoot.toNioPath().toFile()
        context.gradleJdkPath = File(requireJdkHome())
    }

    override fun configureGradleVmOptions(options: MutableSet<String>) {
        super.configureGradleVmOptions(options)
        options.add("-XX:MaxMetaspaceSize=1024m")
        options.add("-XX:+HeapDumpOnOutOfMemoryError")
        options.add("-XX:HeapDumpPath=${System.getProperty("user.dir")}")
    }

    override fun setUpFixtures() {
        myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName()).fixture
        context.mutableCodeInsightTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myTestFixture)
        context.codeInsightTestFixture.setUp()
    }

    override fun tearDownFixtures() {
        runAll(
            { context.codeInsightTestFixture.tearDown() },
            { context.mutableCodeInsightTestFixture = null },
            { resetTestFixture() },
        )
    }

    final override fun importProject(skipIndexing: Boolean?) {
        AndroidStudioTestUtils.specifyAndroidSdk(Path(projectPath))
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

    @Test
    fun testSuiteMethodsAndTestdataFoldersConsistency() {
        fun mutableCaseInsensitiveStringSet(): MutableSet<String> =
            TreeSet(Comparator { o1: String, o2: String -> o1.compareTo(o2, ignoreCase = true) })

        // will point to <testdata-folder of this class>/<methodsAndFoldersAreInOneToOneCorrespondence> (doesn't exist)
        val testDataFolderForThisTest = computeTestDataDirectory(context.description, strict = false)

        val testDataFolderForThisSuite = testDataFolderForThisTest.parentFile

        val actualTestDataFoldersNames = testDataFolderForThisSuite.listFiles()!!
            .mapTo(mutableCaseInsensitiveStringSet()) { it.name }

        val testClass = context.description.testClass
        // NB: Inherited methods are not supported here
        val expectedTestFolders = testClass.declaredMethods.asSequence()
            .filter { it.name.startsWith("test") }
            .mapTo(mutableCaseInsensitiveStringSet()) { getTestFolderName(it.name, it.getAnnotation(TestMetadata::class.java)) }

        val folderNamesPresentOnDiskButNoMethod = actualTestDataFoldersNames - expectedTestFolders

        val errors = buildString {
            if (folderNamesPresentOnDiskButNoMethod.isNotEmpty()) {
                appendLine(
                    "The following folders don't have corresponding test method in ${testClass.simpleName},\n" +
                            "but are present in ${testDataFolderForThisSuite.absolutePath}"
                )
                appendLine("    ${folderNamesPresentOnDiskButNoMethod.joinToString()}")
            }
        }

        if (errors.isNotEmpty()) {
            Assert.fail(
                "Error! Test methods in the test suite are not consistent with test data on disk\n"
                        + errors
            )
        }
    }

    class TestDescriptionProviderJUnitRule(private val testContext: KotlinMppTestsContext) : KotlinBeforeAfterTestRuleWithDescription {
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

    companion object {
        fun KotlinSyncTestsContext.configureByFiles() = WriteAction.runAndWait<Throwable> {
            configureByFiles(testProjectRoot.toPath(), testDataDirectory.toPath(), testConfiguration, testProperties, testFeatures)
        }

        fun configureByFiles(
            testProjectRoot: Path,
            testDataDirectory: Path,
            testConfiguration: TestConfiguration,
            testProperties: KotlinTestProperties,
            testFeatures: List<TestFeature<*>>
        ) {
            assert(testDataDirectory.exists()) { "Directory ${testDataDirectory} doesn't exist" }
            val devModeConfig = testConfiguration.getConfiguration(DevModeTestFeature)
            val writeTestProjectTo = devModeConfig.writeTestProjectTo?.toPath()
            val rootForProjectCopy = computeRootForProjectCopy(writeTestProjectTo, devModeConfig, testDataDirectory.name)
            rootForProjectCopy?.createDirectories()

            WriteAction.runAndWait<Throwable> {
                testDataDirectory.walk()
                    .filter { it.isRegularFile() }
                    .filterNot { it.name.endsWith(KotlinGradleImportingTestCase.AFTER_SUFFIX) }
                    .forEach { source ->
                        val relativePath = testDataDirectory.relativize(source).toCanonicalPath()

                        val text = source.readText()
                            .let(StringUtil::convertLineSeparators)
                            .let(::clearTextFromDiagnosticMarkup)
                            .let { testProperties.substituteKotlinTestPropertiesInText(it, source.toFile()) }
                            .let { initial ->
                                testFeatures.fold(initial) { text, feature ->
                                    feature.preprocessFile(source.toFile(), text) ?: text
                                }
                            }

                        val target = testProjectRoot.resolve(relativePath)
                        val textToWrite = text + target.takeIf { it.exists() }?.let { "\n" + it.readText() }.orEmpty()

                        target.parent.createDirectories()
                        target.writeText(textToWrite)

                        rootForProjectCopy?.resolve(relativePath)?.also { copy ->
                            copy.parent.createDirectories()
                            copy.writeText(textToWrite)
                        }

                        LocalFileSystem.getInstance()
                            .refreshAndFindFileByNioFile(target)!!
                            .putUserData(VfsTestUtil.TEST_DATA_FILE_PATH, source.toCanonicalPath())
                    }
            }
        }

        fun computeRootForProjectCopy(
            writeTestProjectTo: Path?,
            devModeConfig: DevModeTweaksImpl,
            testDirectoryName: String
        ): Path? {
            if (writeTestProjectTo == null) return null

            val rootForProjectCopy = writeTestProjectTo.resolve(testDirectoryName)

            when {
                !writeTestProjectTo.isDirectory() ->
                    error("Trying to write test project to ${writeTestProjectTo}, but it's not a directory")

                rootForProjectCopy.exists() && devModeConfig.overwriteExistingProjectCopy ->
                    rootForProjectCopy.deleteRecursively()

                rootForProjectCopy.exists() && rootForProjectCopy.listDirectoryEntries().isNotEmpty()
                        && !devModeConfig.overwriteExistingProjectCopy ->
                    error("Asked to write test project to ${rootForProjectCopy}, but it's not empty and 'overwriteExisting = true' isn't specified")
            }

            return rootForProjectCopy
        }
    }
}
