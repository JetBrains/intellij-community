package com.intellij.grazie.remote

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieDynamic
import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.download.DownloadableFileService
import org.junit.Assume
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Path

@RunWith(JUnit4::class)
abstract class BundleInfoTestCase: BasePlatformTestCase() {
  @get:Rule
  val temporaryDirectory = TemporaryDirectory()

  override fun setUp() {
    super.setUp()
    GrazieTestBase.maskSaxParserFactory(testRootDisposable)
    Disposer.register(testRootDisposable) { GrazieConfig.update { GrazieConfig.State() } }
  }

  fun assertChecksums(message: String, descriptorProvider: (Lang) -> RemoteLangDescriptor?) {
    // Do not run this test on build server, since artifact downloading will produce flaky failures
    Assume.assumeTrue("Must not be run under TeamCity", !IS_UNDER_TEAMCITY)
    val langs = Lang.entries
    val expected = linkedMapOf<String, String>()
    val actual = linkedMapOf<String, String>()
    for (lang in langs) {
      val descriptor = descriptorProvider(lang) ?: continue
      println("Checking $lang")
      val key = lang.iso.toString().uppercase()
      if (expected.contains(key)) {
        println("Already checked for $key")
        continue
      }
      val path = downloadLanguages(descriptor)
      expected[key] = "private const val ${key}_CHECKSUM = \"${GrazieRemote.checksum(path)}\""
      actual[key] = "private const val ${key}_CHECKSUM = \"${descriptor.checksum}\""
    }
    assertEquals(message, expected.values.joinToString("\n"), actual.values.joinToString("\n"))
  }

  private fun downloadLanguages(descriptor: RemoteLangDescriptor): Path {
    val downloaderService = DownloadableFileService.getInstance()
    val descriptors = listOf(descriptor)
      .map { downloaderService.createFileDescription(it.url, it.storageDescriptor) }
    downloaderService
      .createDownloader(descriptors, "Downloading ${descriptor.iso}")
      .download(GrazieDynamic.dynamicFolder.toFile())
    return GrazieDynamic.dynamicFolder.resolve(descriptor.storageDescriptor)
  }
}
