package com.intellij.grazie.remote

import com.intellij.grazie.GrazieConfig
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
class GrazieLanguageBundleInfoTest: BasePlatformTestCase() {
  @get:Rule
  val temporaryDirectory = TemporaryDirectory()

  /**
   * Test basically doing the same stuff as [com.jetbrains.resharper.external.services.grazie.resources.GrazieResourceBuilder]
   * If it fails with "some-LANG-id' is not a language code known to LanguageTool.", you should add the new language class to
   * the [com.intellij.grazie.remote.RemoteLangDescriptor]
   */
  @Test
  fun `check that all languages are loaded correctly`() {
    Lang.entries.map { lang ->
      var jLanguage = lang.jLanguage
      if (jLanguage == null) {
        val logger = Logger.getInstance(GrazieLanguageBundleInfoTest::class.java)
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
    val languages = Lang.values()
    val expected = linkedMapOf<String, String>()
    val actual = linkedMapOf<String, String>()
    for (language in languages) {
      println("Checking $language")
      val key = language.remote.iso.toString().uppercase()
      if (expected.contains(key)) {
        println("Already checked for $key")
        continue
      }
      val path = downloadBundle(language)
      expected[key] = "private const val ${key}_CHECKSUM = \"${GrazieRemote.checksum(path)}\""
      actual[key] = "private const val ${key}_CHECKSUM = \"${language.remote.checksum}\""
    }
    assertEquals("In case language tool was updated, please update checksums in RemoteLangDescriptor.kt", expected.values.joinToString("\n"), actual.values.joinToString("\n"))
  }

  private fun downloadBundle(language: Lang): Path {
    val downloaderService = DownloadableFileService.getInstance()
    val downloader = downloaderService.createDownloader(
      listOf(downloaderService.createFileDescription(language.remote.url, language.remote.fileName)),
      "Downloading $language"
    )
    val result = downloader.download(temporaryDirectory.createDir().toFile()).single()
    return result.first.toPath()
  }
}
