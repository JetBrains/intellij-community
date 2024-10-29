// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests

import com.intellij.openapi.externalSystem.importing.ImportSpec
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.*
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.*
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
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.workspace.WorkspaceChecksDsl
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.AndroidStudioTestUtils
import org.jetbrains.kotlin.idea.codeInsight.gradle.KotlinGradleImportingTestCase
import org.jetbrains.kotlin.idea.codeInsight.gradle.PluginTargetVersionsRule
import org.jetbrains.kotlin.idea.codeInsight.gradle.combineMultipleFailures
import org.jetbrains.kotlin.idea.codeMetaInfo.clearTextFromDiagnosticMarkup
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
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
import java.io.PrintStream
import java.util.*

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
 * [KotlinMppTestsContext.description] should cover common needs for [Rule]
 *
 * Sharing of the test suite capabilities should be done via standalone composable modularized [TestFeature]s
 *
 * Passing [KotlinPluginMode.K2] will turn on K2 IDE mode and will force use of FIR frontend and all corresponding services.
 * The choice happens during IDE plugins loading, it's not possible to have different tests in one suite for different plugin kinds.
 * K1 and K2 classpaths cannot be mixed together. K2 test suites should belong to a different module than K1 tests, see also
 * [com.intellij.ideaProjectStructure.fast.KotlinModuleConsistencyTest].
 */
@RunWith(KotlinMppTestsJUnit4Runner::class)
@TestDataPath("\$PROJECT_ROOT/community/plugins/kotlin/idea/tests/testData/gradle")
abstract class AbstractKotlinMppGradleImportingTest : GradleImportingTestCase(),
                                                      ExpectedPluginModeProvider,
                                                      WorkspaceChecksDsl, GradleProjectsPublishingDsl, GradleProjectsLinkingDsl,
                                                      HighlightingCheckDsl,
                                                      TestWithKotlinPluginAndGradleVersions, DevModeTweaksDsl,
                                                      AllFilesUnderContentRootConfigurationDsl, RunConfigurationChecksDsl,
                                                      CustomGradlePropertiesDsl, DocumentationCheckerDsl, KotlinMppTestHooksDsl {

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
        KotlinMppTestHooks
    )

    private val context: KotlinMppTestsContextImpl = KotlinMppTestsContextImpl(installedFeatures)

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

    // Temporary hack allowing to reuse new test runner in selected smoke tests for runs on linux-hosts
    open val allowOnNonMac: Boolean = false

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K1

    open fun TestConfigurationDslScope.defaultTestConfiguration() {}

    protected fun doTest(
        runImport: Boolean = true,
        afterImport: (KotlinMppTestsContextImpl) -> Unit = { },
        testSpecificConfiguration: TestConfigurationDslScope.() -> Unit = { },
    ) {
        context.testConfiguration.defaultTestConfiguration()
        context.testConfiguration.testSpecificConfiguration()
        context.doTest(runImport, afterImport)
    }

    private fun KotlinMppTestsContextImpl.doTest(runImport: Boolean, afterImport: (KotlinMppTestsContextImpl) -> Unit = { }) {
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

    private fun KotlinMppTestsContextImpl.runForEnabledFeatures(action: TestFeature<*>.() -> Unit) {
        enabledFeatures.combineMultipleFailures { feature ->
            with(feature) { action() }
        }
    }

    private fun createLocalPropertiesFile() {
        createProjectSubFile(
            "local.properties",
            """
                |sdk.dir=${KotlinTestUtils.getAndroidSdkSystemIndependentPath()}
                |org.gradle.java.home=${findJdkPath()}
            """.trimMargin()
        )
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
        if (!allowOnNonMac) {
            assumeTrue("Test is ignored because it requires Mac-host", HostManager.hostIsMac)
        }

        setUpWithKotlinPlugin {
            // Hack: usually this is set-up by JUnit's Parametrized magic, but
            // our tests source versions from `kotlinTestPropertiesService`, not from
            // @Parametrized
            (this as GradleImportingTestCase).gradleVersion = context.gradleVersion.version
            super.setUp()
        }

        context.testProject = myProject
        context.testProjectRoot = myProjectRoot.toNioPath().toFile()
        context.gradleJdkPath = File(findJdkPath())
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
            { myTestFixture = null },
        )
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
                    val relativeToRoot = it.path.substringAfter(rootDir.path + File.separator)

                    val text = context.testProperties.substituteKotlinTestPropertiesInText(
                        clearTextFromDiagnosticMarkup(FileUtil.loadFile(it, /* convertLineSeparators = */ true)),
                        it
                    )
                    val preprocessedText = installedFeatures.fold(text) { currentText, nextFeature ->
                        nextFeature.preprocessFile(it, currentText) ?: currentText
                    }

                    // Some test features might want to create files before test execution (e.g. to configure
                    // 'gradle.properties'). 'createProjectSubFile' will overwrite pre-existing content, so we're
                    // handling this here.
                    // Note that editing file content *after* 'configureByFiles' isn't very nice as well, as
                    // testFeatures will then have to care about IJ VirtualFile model (and e.g. request VFS refresh)
                    val targetFileInTempDir = File(testProjectRoot, relativeToRoot)
                    val textToWrite = if (targetFileInTempDir.exists())
                        preprocessedText + "\n" + targetFileInTempDir.readText()
                    else
                        preprocessedText

                    val virtualFile = createProjectSubFile(relativeToRoot, textToWrite)
                    if (rootForProjectCopy != null) {
                        val output = File(rootForProjectCopy, relativeToRoot)
                        output.parentFile.mkdirs()
                        output.createNewFile()
                        output.writeText(textToWrite)
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
