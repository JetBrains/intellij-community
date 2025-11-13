// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie

import ai.grazie.nlp.langs.LanguageISO
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
import com.intellij.grazie.utils.TextStyleDomain.*
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.spellchecker.SpellCheckerManager.Companion.getInstance
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.io.ZipUtil
import java.nio.file.Files
import kotlin.io.path.Path

abstract class GrazieTestBase : BasePlatformTestCase() {
  companion object {
    val inspectionTools: Array<LocalInspectionTool> by lazy {
      arrayOf(GrazieInspection(), GrazieInspection.Grammar(), GrazieInspection.Style(), GrazieSpellCheckingInspection())
    }

    /**
     * To speed up test execution, only English is enabled by default.
     *
     * Please use [enableProofreadingFor] if a test requires a specific language
     */
    val enabledLanguages = setOf(Lang.AMERICAN_ENGLISH)
    val enabledRules = setOf("LanguageTool.EN.COMMA_WHICH", "LanguageTool.EN.UPPERCASE_SENTENCE_START", "LanguageTool.DE.MANNSTUNDE")
    val hunspellLangs: Set<Lang> = setOf(Lang.GERMANY_GERMAN, Lang.AUSTRIAN_GERMAN, Lang.SWISS_GERMAN, Lang.RUSSIAN, Lang.UKRAINIAN)

    @JvmStatic
    fun maskSaxParserFactory(disposable: Disposable) {
      val saxParserKey = "javax.xml.parsers.SAXParserFactory"
      val oldSaxParserFactory = System.setProperty(saxParserKey, "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl")
      Disposer.register(disposable) {
        if (oldSaxParserFactory != null) System.setProperty(saxParserKey, oldSaxParserFactory)
        else System.clearProperty(saxParserKey)
      }
    }

    fun loadLangs(langs: Collection<Lang>, project: Project) {
      langs.filter { it in hunspellLangs }.forEach { loadLang(it, project) }
    }

    fun unloadLangs(project: Project) {
      hunspellLangs.forEach { unloadLang(it.iso, project) }
    }

    private fun loadLang(lang: Lang, project: Project) {
      val zipPath = PathManager.getResourceRoot(
        PathManager::class.java.classLoader,
        "dictionary/${lang.iso.name.lowercase()}.aff"
      )
      if (zipPath == null) {
        fail("Hunspell-${lang.iso} not found in classpath")
      }
      val zip = Path(zipPath!!)
      if (!Files.exists(zip)) {
        fail("Hunspell-${lang.iso} not found in classpath")
      }
      val outputDir = GrazieDynamic.getLangDynamicFolder(lang).resolve(lang.hunspellRemote!!.storageName)
      Files.createDirectories(outputDir)
      ZipUtil.extract(zip, outputDir, HunspellDescriptor.filenameFilter())
      getInstance(project).spellChecker!!.addDictionary(lang.dictionary!!)
    }

    private fun unloadLang(iso: LanguageISO, project: Project) {
      val lang = Lang.entries.find { it.iso == iso }!!
      getInstance(project).removeDictionary(getDictionaryPath(lang))
    }

    private fun getDictionaryPath(lang: Lang): String {
      return GrazieDynamic.getLangDynamicFolder(lang).resolve(lang.hunspellRemote!!.file).toString()
    }
  }

  protected open val additionalEnabledRules: Set<String> = emptySet()

  protected open val additionalEnabledContextLanguages: Set<Language> = emptySet()

  override fun getBasePath() = "community/plugins/grazie/src/test/testData"

  override fun setUp() {
    super.setUp()
    maskSaxParserFactory(testRootDisposable)
    myFixture.enableInspections(*inspectionTools)

    enableProofreadingFor(enabledLanguages)

    val newExtensions = TextChecker.allCheckers().map { if (it is LanguageToolChecker) LanguageToolChecker.TestChecker() else it }
    ExtensionTestUtil.maskExtensions(ExtensionPointName("com.intellij.grazie.textChecker"), newExtensions, testRootDisposable)
  }

  override fun tearDown() {
    try {
      GrazieConfig.update { GrazieConfig.State() }
      service<GrazieCheckers>().awaitConfiguration()
      unloadLangs(project)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  @JvmOverloads
  protected fun enableProofreadingFor(
    languages: Set<Lang>,
    domainEnabledRules: MutableMap<TextStyleDomain, Set<String>> = HashMap(),
    domainDisabledRules: MutableMap<TextStyleDomain, Set<String>> = HashMap(),
  ) {
    // Load langs manually to prevent potential deadlock
    val enabledLanguages = languages + GrazieConfig.get().enabledLanguages
    loadLangs(enabledLanguages, project)

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
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  protected open fun runHighlightTestForFile(file: String) {
    myFixture.configureByFile(file)
    myFixture.checkHighlighting(true, false, false)
  }
}
