// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProviderBean
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.IOException

@TestApplication
class FileBasedIndexLayoutProviderBeanTest {
  @ParameterizedTest
  @CsvSource("false, false", "false, true", "true, false", "true, true")
  fun closedProviderIsDiscarded(clearData: Boolean, failOnClose: Boolean) {
    val bean = FileBasedIndexLayoutProviderBean().apply {
      providerClass = TestLayoutProvider::class.java.name
      setPluginDescriptor(DefaultPluginDescriptor(PluginId.getId("test.layout.provider"), TestLayoutProvider::class.java.classLoader))
    }
    val provider = bean.layoutProvider as TestLayoutProvider
    assertSame(provider, bean.layoutProvider, "The bean must cache the active provider")
    provider.failOnClose = failOnClose

    val close = {
      if (clearData) bean.closeAndClearLayoutProvider() else bean.closeLayoutProvider()
    }
    if (failOnClose) {
      assertThrows(IOException::class.java) { close() }
    }
    else {
      close()
    }

    assertTrue(provider.closed, "The bean must close the old provider")
    assertEquals(clearData, provider.dataCleared, "Only index deletion must clear the provider's data")
    assertNotSame(provider, bean.layoutProvider, "The bean must discard the closed provider, even if closing fails")
    bean.closeLayoutProvider()
  }

  class TestLayoutProvider : FileBasedIndexLayoutProvider {
    var failOnClose: Boolean = false
    var closed: Boolean = false
    var dataCleared: Boolean = false

    override fun <K, V> getLayout(
      extension: FileBasedIndexExtension<K, V>,
      otherApplicableProviders: Iterable<FileBasedIndexLayoutProvider>,
    ): VfsAwareIndexStorageLayout<K, V> = throw UnsupportedOperationException()

    override fun close() {
      closed = true
      if (failOnClose) throw IOException("Cannot close the test provider")
    }

    override fun closeAndClearData() {
      dataCleared = true
      close()
    }
  }
}
