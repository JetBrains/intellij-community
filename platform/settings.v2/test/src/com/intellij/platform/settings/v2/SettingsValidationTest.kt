// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * A key's [SettingsKey.validator] serves both a live, non-throwing check ([SettingsKey.validate], for a UI) and
 * write-time enforcement (an invalid write throws [SettingsValidationException]; a valid one persists). A
 * validator that throws counts as a rejection in both.
 */
@TestApplication
class SettingsValidationTest {
  companion object {
    private const val REASON = "port must be in 1..65535"
    private val port = SettingsKey("test.validation.port", default = 8080, validator = { if (it in 1..65535) null else REASON })
    private val throwing = SettingsKey("test.validation.throwing", default = 1, validator = { check(it != 0) { "zero" }; null })
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `validate returns null for a valid value and the reason for an invalid one`() {
    assertNull(port.validate(80))
    assertNull(port.validate(65535))
    assertEquals(REASON, port.validate(0))
    assertEquals(REASON, port.validate(70000))
  }

  @Test
  fun `an invalid write is rejected while a valid one persists`(): Unit = runBlocking {
    val provider = TestLayerProvider()
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val app = SettingsContext.app()

    val rejected = runCatching { port[app] = 70000 }.exceptionOrNull()
    assertInstanceOf(SettingsValidationException::class.java, rejected)
    assertEquals(REASON, (rejected as SettingsValidationException).reason)
    assertEquals(8080, port[app])  // the rejected write never reached a layer; still the default

    port[app] = 9090
    awaitEquals(9090) { port[app] }
  }

  @Test
  fun `a validator that throws rejects the value in validate and logs the error`() {
    val (reason, errors) = collectingErrors { throwing.validate(0) }

    assertNotNull(reason)
    assertEquals(listOf("Validator of settings key '${throwing.identity}' failed"), errors)
    assertNull(throwing.validate(5))
  }

  @Test
  fun `a write of a value whose validator throws fails and stores nothing`(): Unit = runBlocking {
    val provider = TestLayerProvider()
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val app = SettingsContext.app()

    val (rejected, _) = collectingErrors { runCatching { throwing[app] = 0 }.exceptionOrNull() }
    assertInstanceOf(SettingsValidationException::class.java, rejected)
    assertEquals(1, throwing[app])
    SettingsVault.getInstance().flush(app)
    for (locality in LayerLocality.entries) {
      assertNull(provider.saved(app, locality)?.values?.get(throwing.identity)) { "$locality" }
    }
  }
}
