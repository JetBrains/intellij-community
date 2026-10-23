// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.io.path.writeLines

private const val KEY = LocalizationUtil.LOCALIZATION_KEY

@TestApplication
internal class LanguageBundleListenerTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private val file: Path
    get() = PathManager.getOriginalConfigDir().resolve(EarlyAccessRegistryManager.fileName)

  private var originalContent: ByteArray? = null

  @BeforeEach
  fun setUp() {
    // the registry manager installs the listener that publishes `RegistryManager.TOPIC`
    RegistryManager.getInstance()
    originalContent = if (file.exists()) file.readBytes() else null
    file.deleteIfExists()
  }

  @AfterEach
  fun tearDown() {
    val content = originalContent
    if (content == null) {
      file.deleteIfExists()
    }
    else {
      file.writeBytes(content)
    }
  }

  @Test
  @RegistryKey(key = KEY, value = "ja")
  fun `the same value in the file does not change the registry`() {
    writeFile(KEY, "ja")

    val changedKeys = closeAppAndCollectChangedKeys()

    assertThat(changedKeys).isEmpty()
    assertThat(Registry.stringValue(KEY)).isEqualTo("ja")
  }

  @Test
  @RegistryKey(key = KEY, value = "ja")
  fun `a different value in the file updates the registry`() {
    writeFile(KEY, "zh-CN")

    val changedKeys = closeAppAndCollectChangedKeys()

    assertThat(changedKeys).containsExactly(KEY)
    assertThat(Registry.stringValue(KEY)).isEqualTo("zh-CN")
  }

  @Test
  @RegistryKey(key = KEY, value = "ja")
  fun `a file without the key does not change the registry`() {
    writeFile("ide.experimental.ui", "true")

    val changedKeys = closeAppAndCollectChangedKeys()

    assertThat(changedKeys).isEmpty()
    assertThat(Registry.stringValue(KEY)).isEqualTo("ja")
  }

  @Test
  @RegistryKey(key = KEY, value = "ja")
  fun `a missing file does not change the registry`() {
    val changedKeys = closeAppAndCollectChangedKeys()

    assertThat(changedKeys).isEmpty()
    assertThat(Registry.stringValue(KEY)).isEqualTo("ja")
  }

  /**
   * Subscribes right before the call, so the registry writes of [RegistryKey] stay out of the result.
   */
  private fun closeAppAndCollectChangedKeys(): List<String> {
    val changedKeys = mutableListOf<String>()
    ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(RegistryManager.TOPIC, object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        changedKeys.add(value.key)
      }
    })
    LanguageBundleListener().appClosing()
    return changedKeys
  }

  private fun writeFile(key: String, value: String) {
    file.createParentDirectories().writeLines(listOf(key, value))
  }
}
