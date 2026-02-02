package com.intellij.grazie.remote

import org.junit.Test

internal class HunspellBundleInfoTest: BundleInfoTestCase() {

  @Test
  fun `verify hardcoded checksums are valid`() {
    assertChecksums("In case Grazie rule engine was updated, please update checksums in HunspellDescriptor.kt") {
      it.hunspellRemote
    }
  }
}
