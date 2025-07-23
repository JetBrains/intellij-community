// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie

import ai.grazie.nlp.langs.LanguageISO
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.grazie.grammar.LanguageToolChecker
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.remote.HunspellDescriptor
import com.intellij.grazie.spellcheck.GrazieCheckers
import com.intellij.grazie.text.TextChecker
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.text.TextProblem
import com.intellij.grazie.utils.filterFor
import com.intellij.lang.Language
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPlainText
import com.intellij.spellchecker.SpellCheckerManager.Companion.getInstance
import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.io.ZipUtil
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.Path

abstract class GrazieTestBase : BasePlatformTestCase() {
  companion object {
    val inspectionTools by lazy {
      arrayOf<LocalInspectionTool>(GrazieInspection(), SpellCheckingInspection())
    }

    /**
     * To speed up test execution, only English is enabled by default.
     *
     * Please use [enableProofreadingFor] if a test requires a specific language
     */
    val enabledLanguages = setOf(Lang.AMERICAN_ENGLISH)
    val enabledRules = setOf("LanguageTool.EN.COMMA_WHICH", "LanguageTool.EN.UPPERCASE_SENTENCE_START")
  }

  private val hunspellLangs: Set<Lang> = setOf(Lang.GERMANY_GERMAN, Lang.AUSTRIAN_GERMAN, Lang.SWISS_GERMAN, Lang.RUSSIAN, Lang.UKRAINIAN)

  protected open val additionalEnabledRules: Set<String> = emptySet()

  protected open val additionalEnabledContextLanguages: Set<Language> = emptySet()

  override fun getBasePath() = "community/plugins/grazie/src/test/testData"

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(*inspectionTools)

    enableProofreadingFor(enabledLanguages)

    val newExtensions = TextChecker.allCheckers().map { if (it is LanguageToolChecker) LanguageToolChecker.TestChecker() else it }
    ExtensionTestUtil.maskExtensions(ExtensionPointName("com.intellij.grazie.textChecker"), newExtensions, testRootDisposable)
  }

  override fun tearDown() {
    try {
      GrazieConfig.update { GrazieConfig.State() }
      service<GrazieCheckers>().awaitConfiguration()
      hunspellLangs.forEach { unloadLang(it.iso) }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  protected fun enableProofreadingFor(languages: Set<Lang>) {
    // Load langs manually to prevent potential deadlock
    val enabledLanguages = languages + GrazieConfig.get().enabledLanguages
    enabledLanguages.filter { it in hunspellLangs }.toMutableList().forEach { loadLang(it) }

    GrazieConfig.update { state ->
      val checkingContext = state.checkingContext.copy(
        isCheckInStringLiteralsEnabled = true,
        isCheckInCommentsEnabled = true,
        isCheckInDocumentationEnabled = true,
        enabledLanguages = additionalEnabledContextLanguages.map { it.id }.toSet(),
      )
      state.copy(
        enabledLanguages = enabledLanguages,
        userEnabledRules = enabledRules + additionalEnabledRules,
        checkingContext = checkingContext
      )
    }

    service<GrazieCheckers>().awaitConfiguration()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  protected open fun runHighlightTestForFile(file: String) {
    myFixture.configureByFile(file)
    myFixture.checkHighlighting(true, false, false)
  }

  protected fun runHighlightTestForFileUsingGrazieSpellchecker(file: String) {
    Registry.get("spellchecker.grazie.enabled").setValue(true, testRootDisposable)
    runHighlightTestForFile(file)
  }

  fun plain(vararg texts: String) = plain(texts.toList())

  fun plain(texts: List<String>): Collection<PsiElement> {
    return texts.flatMap { myFixture.configureByText("${it.hashCode()}.txt", it).filterFor<PsiPlainText>() }
  }

  fun check(tokens: Collection<PsiElement>): List<TextProblem> {
    return tokens.flatMap {
      TextExtractor.findTextsAt(it, TextContent.TextDomain.ALL).flatMap { text ->
        runBlocking {
          LanguageToolChecker().check(text)
        }
      }
    }
  }

  private fun loadLang(lang: Lang) {
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

  private fun unloadLang(iso: LanguageISO) {
    try {
      val lang = Lang.entries.find { it.iso == iso }!!
      getInstance(project).removeDictionary(getDictionaryPath(lang))
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
  }

  private fun getDictionaryPath(lang: Lang): String {
    return GrazieDynamic.getLangDynamicFolder(lang).resolve(lang.hunspellRemote!!.file).toString()
  }
}
