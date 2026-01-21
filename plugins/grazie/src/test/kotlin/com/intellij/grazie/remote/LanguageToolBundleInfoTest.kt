package com.intellij.grazie.remote

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.text.TextChecker
import com.intellij.openapi.diagnostic.Logger
import org.junit.Assume
import org.junit.Test

class LanguageToolBundleInfoTest : BundleInfoTestCase() {
  /**
   * Test basically doing the same stuff as [com.jetbrains.resharper.external.services.grazie.resources.GrazieResourceBuilder]
   * If it fails with "some-LANG-id" is not a language code known to LanguageTool.", you should add the new language class to
   * the [com.intellij.grazie.remote.LanguageToolDescriptor]
   */
  @Test
  fun `check that all languages are loaded correctly`() {
    // Do not run this test on build server, since artifact downloading will produce flaky failures
    Assume.assumeTrue("Must not be run under TeamCity", !IS_UNDER_TEAMCITY)
    Lang.entries.filter { it.ltRemote != null }.forEach { lang ->
      var jLanguage = lang.jLanguage
      if (jLanguage == null) {
        val logger = Logger.getInstance(LanguageToolBundleInfoTest::class.java)
        logger.info("Language pack for ${lang.displayName} not found, downloading...")
        if (!GrazieRemote.downloadWithoutLicenseCheck(lang)) {
          logger.info("Failed to download language pack for ${lang.displayName}")
          return@forEach
        }
        GrazieConfig.update { state -> state.copy(enabledLanguages = state.enabledLanguages + lang) }
        jLanguage = lang.jLanguage!!
      }
      TextChecker.allCheckers().flatMap { it.getRules(jLanguage.localeWithCountryAndVariant) }
    }
  }

  @Test
  fun `verify hardcoded checksums are valid`() {
    assertChecksums("In case language tool was updated, please update checksums in RemoteLangDescriptor.kt") {
      it.ltRemote
    }
  }
}
