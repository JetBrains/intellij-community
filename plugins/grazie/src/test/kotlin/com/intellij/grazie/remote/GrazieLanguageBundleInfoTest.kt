package com.intellij.grazie.remote

import com.intellij.grazie.jlanguage.Lang
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.download.DownloadableFileService
import org.hamcrest.core.IsEqual
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Path

@RunWith(JUnit4::class)
class GrazieLanguageBundleInfoTest: BasePlatformTestCase() {
  @get:Rule
  val temporaryDirectory = TemporaryDirectory()

  @get:Rule
  val errors = ErrorCollector()

  @Test
  fun `verify hardcoded checksums are valid`() {
    // Do not run this test on build server, since artifact downloading will produce flaky failures
    Assume.assumeTrue("Must not be run under TeamCity", !UsefulTestCase.IS_UNDER_TEAMCITY)
    val languages = Lang.values()
    for (language in languages) {
      println("Checking $language")
      val path = downloadBundle(language)
      errors.checkThat(
        "Checksum of the downloaded language bundle for $language is not the same as specified in the language description",
        GrazieRemote.checksum(path),
        IsEqual.equalTo(language.remote.checksum)
      )
    }
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
