// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grazie

import ai.grazie.rules.settings.TextStyle
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.grazie.grammar.LanguageToolChecker
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.remote.HunspellDescriptor
import com.intellij.grazie.spellcheck.GrazieCheckers
import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection
import com.intellij.grazie.text.TextChecker
import com.intellij.grazie.utils.TextStyleDomain
import com.intellij.grazie.utils.TextStyleDomain.AIPrompt
import com.intellij.grazie.utils.TextStyleDomain.CodeComment
import com.intellij.grazie.utils.TextStyleDomain.CodeDocumentation
import com.intellij.grazie.utils.TextStyleDomain.Commit
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiManager
import com.intellij.spellchecker.ProjectDictionaryLayer
import com.intellij.spellchecker.SpellCheckerManager
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.BazelTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.io.ZipUtil
import java.nio.file.Files
import kotlin.io.path.Path
import org.jetbrains.annotations.TestOnly

/**
 * Creates a [GrazieTestFixture] on a new module of [projectFixture].
 * Declare the result as an instance property, so that each test gets its own fixture.
 * Declare [projectFixture] as a static field, so that the tests of the class share the project.
 *
 * [testDataPath] is the test data directory of the [CodeInsightTestFixture].
 * [additionalEnabledRules] are the rules that the fixture enables in addition to [GrazieTestFixture.enabledRules].
 * [additionalEnabledContextLanguages] are the languages that the fixture enables in the checking context.
 * [projectDescriptor] configures the new module. It must be a [DefaultLightProjectDescriptor].
 * [moduleTypeId] is the type of the new module. By default, it is the module type of [projectDescriptor].
 */
@TestOnly
@JvmOverloads
fun grazieTestFixture(
  projectFixture: TestFixture<Project>,
  testDataPath: String = GrazieTestFixture.defaultTestDataPath(),
  additionalEnabledRules: Set<String> = emptySet(),
  additionalEnabledContextLanguages: Set<Language> = emptySet(),
  projectDescriptor: LightProjectDescriptor? = null,
  moduleTypeId: String = projectDescriptor?.moduleTypeId ?: "",
): TestFixture<GrazieTestFixture> = testFixture("grazieTestFixture") { context ->
  val fixture = codeInsightModuleFixture(projectFixture, moduleTypeId).init()
  fixture.testDataPath = testDataPath
  val result = GrazieTestFixture(fixture, context.extensionContext.requiredTestMethod.name, additionalEnabledRules, additionalEnabledContextLanguages)
  result.setUp(projectDescriptor)
  initialized(result) {
    result.tearDown()
  }
}

/**
 * The Grazie test helpers for a JUnit 5 test.
 * The fixture enables the Grazie inspections and the [enabledLanguages], and it resets [GrazieConfig] after the test.
 * Call a member from a background thread. A member takes the EDT calls that it needs.
 */
@TestOnly
class GrazieTestFixture internal constructor(
  val fixture: CodeInsightTestFixture,
  val testMethodName: String,
  private val additionalEnabledRules: Set<String>,
  private val additionalEnabledContextLanguages: Set<Language>,
) {
  val project: Project get() = fixture.project

  /**
   * The disposable of the test. The [fixture] disposes it after the test.
   * A lambda that you register here runs after the teardown of this fixture.
   * So it must not read a property that delegates to this fixture. Capture the values before the registration.
   */
  val testRootDisposable: Disposable get() = fixture.testRootDisposable

  /**
   * The name of the test method without the `test` prefix, as `UsefulTestCase.getTestName` gives it.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = PlatformTestUtil.getTestName(testMethodName, lowercaseFirstLetter)

  internal fun setUp(projectDescriptor: LightProjectDescriptor?) {
    if (projectDescriptor != null) {
      setUpModule(projectDescriptor)
    }
    fixture.enableInspections(*inspectionTools)

    maskSaxParserFactory(testRootDisposable)
    installTestChecker(testRootDisposable)
    enableProofreadingFor(enabledLanguages)
  }

  private fun setUpModule(projectDescriptor: LightProjectDescriptor) {
    check(projectDescriptor is DefaultLightProjectDescriptor) { "Only a DefaultLightProjectDescriptor is supported: $projectDescriptor" }
    val module = fixture.module
    val sdk = projectDescriptor.sdk
    if (sdk != null) {
      WriteAction.runAndWait<RuntimeException> { ProjectJdkTable.getInstance().addJdk(sdk, testRootDisposable) }
      ModuleRootModificationUtil.setModuleSdk(module, sdk)
    }
    ModuleRootModificationUtil.updateModel(module) { projectDescriptor.configureModule(module, it, it.contentEntries.single()) }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }

  internal fun tearDown() {
    GrazieConfig.update { GrazieConfig.State() }
    service<GrazieCheckers>().awaitConfiguration()
  }

  /**
   * Enables the proofreading for [languages] and for the languages that are already enabled.
   * The rules of [domainEnabledRules] and [domainDisabledRules] replace the default rules of their domains.
   */
  @JvmOverloads
  fun enableProofreadingFor(
    languages: Set<Lang>,
    domainEnabledRules: MutableMap<TextStyleDomain, Set<String>> = HashMap(),
    domainDisabledRules: MutableMap<TextStyleDomain, Set<String>> = HashMap(),
  ) {
    // Load langs manually to prevent potential deadlock
    val enabledLanguages = languages + GrazieConfig.get().enabledLanguages
    loadLangs(enabledLanguages, testRootDisposable)

    GrazieConfig.update { state ->
      val checkingContext = state.checkingContext.copy(
        isCheckInStringLiteralsEnabled = true,
        isCheckInCommentsEnabled = true,
        isCheckInDocumentationEnabled = true,
        enabledLanguages = additionalEnabledContextLanguages.map { it.id }.toSet(),
      )
      val domains = setOf(Commit, AIPrompt, CodeDocumentation, CodeComment)
      if (domainEnabledRules.isEmpty()) {
        domains.forEach { domainEnabledRules[it] = enabledRules + additionalEnabledRules }
      }
      state.copy(
        enabledLanguages = enabledLanguages,
        userEnabledRules = enabledRules + additionalEnabledRules,
        checkingContext = checkingContext,
        styleProfile = TextStyle.Unspecified.id,
        domainEnabledRules = domainEnabledRules,
        domainDisabledRules = domainDisabledRules,
      )
    }

    service<GrazieCheckers>().awaitConfiguration()
  }

  /**
   * Configures the [fixture] with the test data [file], and checks its warnings.
   */
  fun runHighlightTestForFile(file: String) {
    fixture.configureByFile(file)
    fixture.checkHighlighting(true, false, false)
  }

  /**
   * Saves [word] to the project dictionary until the end of the test.
   */
  fun saveWord(word: String) {
    val layer = ProjectDictionaryLayer(project)
    SpellCheckerManager.getInstance(project).acceptWordAsCorrect(word, null, project, layer)
    Disposer.register(testRootDisposable) { layer.dictionary.removeFromDictionary(word) }
  }

  /**
   * Drops the PSI caches of the [project] on the EDT, for example in the setup of a benchmark.
   */
  fun dropPsiCaches() {
    runInEdtAndWait { PsiManager.getInstance(project).dropPsiCaches() }
  }

  companion object {
    @JvmStatic
    val inspectionTools: Array<LocalInspectionTool> by lazy {
      arrayOf(GrazieInspection(), GrazieInspection.Grammar(), GrazieInspection.Style(), GrazieSpellCheckingInspection())
    }

    /**
     * To speed up test execution, only English is enabled by default.
     *
     * Please use [enableProofreadingFor] if a test requires a specific language
     */
    @JvmStatic
    val enabledLanguages: Set<Lang> = setOf(Lang.AMERICAN_ENGLISH)

    @JvmStatic
    val enabledRules: Set<String> = setOf("LanguageTool.EN.COMMA_WHICH", "LanguageTool.EN.UPPERCASE_SENTENCE_START", "LanguageTool.DE.MANNSTUNDE")

    @JvmStatic
    val hunspellLangs: Set<Lang> = setOf(Lang.GERMANY_GERMAN, Lang.AUSTRIAN_GERMAN, Lang.SWISS_GERMAN, Lang.RUSSIAN, Lang.UKRAINIAN)

    /**
     * The test data directory of the Grazie plugin, also under Bazel.
     */
    @JvmStatic
    fun defaultTestDataPath(): String {
      if (BazelTestUtil.isUnderBazelTest) {
        val label = BazelLabel.fromString("@community//plugins/grazie/src/test:testData")
        return BazelTestUtil.getFileFromBazelRuntime(label).toString()
      }
      return Path(PlatformTestUtil.getCommunityPath(), "plugins/grazie/src/test/testData").toString()
    }

    @JvmStatic
    fun maskSaxParserFactory(disposable: Disposable) {
      val saxParserKey = "javax.xml.parsers.SAXParserFactory"
      val oldSaxParserFactory = System.setProperty(saxParserKey, "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl")
      Disposer.register(disposable) {
        if (oldSaxParserFactory != null) System.setProperty(saxParserKey, oldSaxParserFactory)
        else System.clearProperty(saxParserKey)
      }
    }

    @JvmStatic
    fun installTestChecker(disposable: Disposable) {
      val newExtensions = TextChecker.allCheckers().map { if (it is LanguageToolChecker) LanguageToolChecker.TestChecker() else it }
      ExtensionTestUtil.maskExtensions(ExtensionPointName("com.intellij.grazie.textChecker"), newExtensions, disposable)
    }

    /**
     * Extracts the Hunspell dictionaries of [langs] until [disposable] is disposed.
     */
    @JvmStatic
    fun loadLangs(langs: Collection<Lang>, disposable: Disposable) {
      langs.filter { it in hunspellLangs }.forEach { loadLang(it, disposable) }
    }

    private fun loadLang(lang: Lang, disposable: Disposable) {
      val zipPath = PathManager.getResourceRoot(
        PathManager::class.java.classLoader,
        "dictionary/${lang.iso.name.lowercase()}.aff"
      ) ?: throw AssertionError("Hunspell-${lang.iso} not found in classpath")
      val zip = Path(zipPath)
      if (!Files.exists(zip)) {
        throw AssertionError("Hunspell-${lang.iso} not found in classpath")
      }
      val outputDir = GrazieDynamic.dynamicFolder.resolve(lang.hunspellRemote!!.storageName)
      Files.createDirectories(outputDir)
      ZipUtil.extract(zip, outputDir, HunspellDescriptor.filenameFilter())
      lang.dictionary!! // pre initialize dictionary
      Disposer.register(disposable) { NioFiles.deleteRecursively(outputDir) }
    }
  }
}
