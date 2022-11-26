package com.intellij.grazie

import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import org.languagetool.JLanguageTool

internal class LanguageToolVersionTest: UsefulTestCase() {
  fun `test versions match`() {
    val versionForDownloads = GraziePlugin.LanguageTool.version
    val compileVersion = JLanguageTool.VERSION
    val message = """
    Hardcoded version for downloading language bundles should match the version plugin was compiled with.
    Verify that LanguageTool dependency in [intellij.grazie.core] and [intellij.grazie.tests] use the same correct version.
    [com.intellij.grazie.GraziePlugin.LanguageTool.version] should be set to the correct version as well.
    """.trimIndent()
    TestCase.assertEquals(message, versionForDownloads, compileVersion)
  }
}
