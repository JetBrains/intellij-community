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
      val checksum = GrazieRemote.checksum(path)
      val contentChecksum = if (descriptor.contentChecksum == descriptor.checksum) checksum else checksum(path)
      expected[key] = formatChecksums(key, checksum, contentChecksum)
      actual[key] = formatChecksums(key, descriptor.checksum, descriptor.contentChecksum)
    }
    assertEquals(message, expected.values.joinToString("\n"), actual.values.joinToString("\n"))
  }

  protected open fun checksum(path: Path): String = GrazieRemote.checksum(path)

  private fun formatChecksums(key: String, checksum: String, contentChecksum: String): String {
    if (checksum == contentChecksum) return "private const val ${key}_CHECKSUM = \"$checksum\""
    return "private const val ${key}_JAR_CHECKSUM = \"$checksum\"\n" +
           "private const val ${key}_CONTENT_CHECKSUM = \"$contentChecksum\""
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
