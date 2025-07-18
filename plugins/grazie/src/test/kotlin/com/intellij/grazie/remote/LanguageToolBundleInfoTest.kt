package com.intellij.grazie.remote

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.text.TextChecker
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.download.DownloadableFileService
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Path

@RunWith(JUnit4::class)
class LanguageToolBundleInfoTest : BasePlatformTestCase() {
  @get:Rule
  val temporaryDirectory = TemporaryDirectory()

  /**
   * Test basically doing the same stuff as [com.jetbrains.resharper.external.services.grazie.resources.GrazieResourceBuilder]
   * If it fails with "some-LANG-id' is not a language code known to LanguageTool.", you should add the new language class to
   * the [com.intellij.grazie.remote.LanguageToolDescriptor]
   */
  @Test
  fun `check that all languages are loaded correctly`() {
    Lang.entries.filter { it.ltRemote != null }.map { lang ->
      var jLanguage = lang.jLanguage
      if (jLanguage == null) {
        val logger = Logger.getInstance(LanguageToolBundleInfoTest::class.java)
        logger.info("Language pack for ${lang.displayName} not found, downloading...")
        if (!GrazieRemote.download(lang)) {
          logger.info("Failed to download language pack for ${lang.displayName}")
          return@map
        }
        GrazieConfig.update { state -> state.copy(enabledLanguages = state.enabledLanguages + lang) }
        jLanguage = lang.jLanguage!!
      }
      TextChecker.allCheckers().flatMap { it.getRules(jLanguage.localeWithCountryAndVariant) }
    }
  }

  @Test
  fun `verify hardcoded checksums are valid`() {
    // Do not run this test on build server, since artifact downloading will produce flaky failures
    Assume.assumeTrue("Must not be run under TeamCity", !IS_UNDER_TEAMCITY)
    val langs = Lang.entries
    val expected = linkedMapOf<String, String>()
    val actual = linkedMapOf<String, String>()
    for (lang in langs) {
      println("Checking $lang")
      val key = lang.iso.toString().uppercase()
      if (expected.contains(key)) {
        println("Already checked for $key")
        continue
      }
      val path = downloadLanguages(lang)
      expected[key] = "private const val ${key}_CHECKSUM = \"${GrazieRemote.checksum(path)}\""
      actual[key] = "private const val ${key}_CHECKSUM = \"${lang.ltRemote!!.checksum}\""
    }
    assertEquals("In case language tool was updated, please update checksums in RemoteLangDescriptor.kt", expected.values.joinToString("\n"), actual.values.joinToString("\n"))
  }

  // copy-paste from [LanguageDownloader]
  private fun downloadLanguages(lang: Lang): Path {
    val downloaderService = DownloadableFileService.getInstance()
    val folder = GrazieDynamic.getLangDynamicFolder(lang)
    val descriptors = listOf(lang.ltRemote!!)
      .map { downloaderService.createFileDescription(it.url, it.storageName) }
    downloaderService
      .createDownloader(descriptors, "Downloading ${lang.iso}")
      .download(folder.toFile())
    return folder.resolve(lang.ltRemote!!.storageName)
  }
}
