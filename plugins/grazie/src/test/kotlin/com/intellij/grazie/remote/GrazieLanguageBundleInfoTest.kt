package com.intellij.grazie.remote

import com.intellij.grazie.jlanguage.Lang
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
