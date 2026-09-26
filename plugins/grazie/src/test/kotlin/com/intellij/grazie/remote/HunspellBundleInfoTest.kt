package com.intellij.grazie.remote

import com.intellij.util.io.ZipUtil
import org.junit.Test
import java.nio.file.Path

internal class HunspellBundleInfoTest: BundleInfoTestCase() {

  override fun checksum(path: Path): String {
    val outputDir = temporaryDirectory.createDir()
    ZipUtil.extract(path, outputDir, HunspellDescriptor.filenameFilter())
    return GrazieRemote.checksum(outputDir)
  }

  @Test
  fun `verify hardcoded checksums are valid`() {
    assertChecksums("In case Grazie rule engine was updated, please update checksums in HunspellDescriptor.kt") {
      it.hunspellRemote
    }
  }
}
